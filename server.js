// m3u8-viewer — servidor sin dependencias (solo Node.js >= 18)
// Sirve la interfaz web y ofrece un proxy con CORS para reproducir
// streams m3u8 (HLS) que de otro modo bloquearía el navegador.

const http = require("http");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");

const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = path.join(__dirname, "public");
const DEV = process.env.DEV === "1" || process.argv.includes("--dev");

// ---- Live reload (SSE, sin dependencias) ------------------------------------
const sseClients = new Set();

function notifyReload() {
  for (const res of sseClients) {
    try { res.write("event: reload\ndata: 1\n\n"); } catch {}
  }
}

if (DEV) {
  try {
    fs.watch(PUBLIC_DIR, { recursive: true }, () => notifyReload());
    console.log("  [dev] live-reload activo, observando public/");
  } catch (e) {
    console.log("  [dev] no se pudo observar public/:", e.message);
  }
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".ico": "image/x-icon",
};

// ---- Proxy con reescritura de manifiestos HLS -------------------------------
// raw = true  -> devuelve el manifiesto SIN reescribir (para listar playlists)
async function handleProxy(req, res, target, raw = false) {
  let upstream;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 12000); // 12s máx.
  try {
    upstream = await fetch(target, {
      headers: {
        // Algunos CDNs exigen un User-Agent "normal"
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
      },
      redirect: "follow",
      signal: ctrl.signal,
    });
  } catch (err) {
    clearTimeout(timer);
    const msg = err.name === "AbortError"
      ? "El origen no respondió a tiempo (timeout)"
      : "No se pudo conectar con el origen: " + err.message;
    res.writeHead(504, { "Content-Type": "text/plain" });
    res.end(msg);
    return;
  }
  clearTimeout(timer);

  // Propaga errores del origen (404, 403 geobloqueo, etc.) de forma clara
  if (!upstream.ok) {
    res.writeHead(upstream.status, {
      "Access-Control-Allow-Origin": "*",
      "Content-Type": "text/plain",
    });
    res.end(`El canal devolvió ${upstream.status} (probablemente caído o bloqueado)`);
    return;
  }

  const contentType = upstream.headers.get("content-type") || "";
  const isManifest =
    /mpegurl/i.test(contentType) ||
    /\.m3u8(\?|$)/i.test(target) ||
    /\.m3u(\?|$)/i.test(target);

  const baseCors = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "*",
  };

  if (isManifest) {
    const text = await upstream.text();
    const out = raw ? text : rewriteManifest(text, upstream.url || target);
    res.writeHead(upstream.status, {
      ...baseCors,
      "Content-Type": "application/vnd.apple.mpegurl",
    });
    res.end(out);
    return;
  }

  // Segmentos y demás binarios: pasarlos tal cual
  res.writeHead(upstream.status, {
    ...baseCors,
    "Content-Type": contentType || "application/octet-stream",
  });
  const buf = Buffer.from(await upstream.arrayBuffer());
  res.end(buf);
}

// Reescribe URLs (segmentos, sub-playlists, llaves) para que pasen por el proxy
function rewriteManifest(text, baseUrl) {
  const toProxy = (u) => {
    try {
      const abs = new URL(u, baseUrl).href;
      return "/proxy?url=" + encodeURIComponent(abs);
    } catch {
      return u;
    }
  };

  return text
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;

      // Atributos con URI="..." (llaves EXT-X-KEY, mapas, etc.)
      if (trimmed.startsWith("#")) {
        return line.replace(/URI="([^"]+)"/g, (_m, u) => `URI="${toProxy(u)}"`);
      }

      // Línea de recurso (segmento o sub-playlist)
      return toProxy(trimmed);
    })
    .join("\n");
}

// ---- Archivos estáticos -----------------------------------------------------
function serveStatic(req, res, pathname) {
  let filePath = path.join(
    PUBLIC_DIR,
    pathname === "/" ? "index.html" : pathname
  );

  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain" });
      res.end("No encontrado");
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { "Content-Type": MIME[ext] || "text/plain" });
    res.end(data);
  });
}

// ---- Servidor ---------------------------------------------------------------
const server = http.createServer((req, res) => {
  const parsed = new URL(req.url, `http://${req.headers.host}`);

  if (parsed.pathname === "/livereload") {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    res.write("retry: 500\n\n");
    sseClients.add(res);
    req.on("close", () => sseClients.delete(res));
    return;
  }

  if (parsed.pathname === "/proxy") {
    const target = parsed.searchParams.get("url");
    const raw = parsed.searchParams.get("raw") === "1";
    if (!target) {
      res.writeHead(400, { "Content-Type": "text/plain" });
      res.end('Falta el parámetro "url"');
      return;
    }
    handleProxy(req, res, target, raw).catch((err) => {
      if (!res.headersSent) res.writeHead(500);
      res.end("Error interno: " + err.message);
    });
    return;
  }

  serveStatic(req, res, parsed.pathname);
});

server.listen(PORT, () => {
  console.log(`\n  m3u8-viewer en marcha  →  http://localhost:${PORT}\n`);
});
