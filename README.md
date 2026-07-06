# m3u8 Viewer

Pequeña app en Node.js para **visualizar streams `.m3u8` (HLS)** y cargar
playlists **M3U / IPTV** (como las de [Free-TV/IPTV](https://github.com/Free-TV/IPTV)).

Sin dependencias externas: solo necesitas **Node.js 18 o superior**.

## Uso

```bash
cd m3u8-viewer
npm start
```

Luego abre **http://localhost:3000** en tu navegador.

Puedes cambiar el puerto:

```bash
PORT=8080 npm start
```

## Modo desarrollo (watch + recarga en vivo)

```bash
npm run dev
```

- Reinicia el servidor automáticamente al guardar `server.js` (`node --watch`).
- Recarga el navegador solo al guardar cualquier archivo de `public/`
  (live-reload por SSE, sin dependencias). Requiere **Node 18.11+**.

## Compartir: AirDrop, AirPlay y Picture-in-Picture

Bajo el vídeo hay tres botones:

- **Compartir (AirDrop)**: abre la hoja de compartir del sistema, donde aparece
  AirDrop, para enviar el **enlace del canal**. Funciona en **Safari (macOS/iOS)**
  mediante la Web Share API. En navegadores que no la soportan (Firefox de
  escritorio), copia el enlace al portapapeles.
- **AirPlay**: envía el vídeo a un Apple TV / pantalla compatible. Solo **Safari**.
- **Picture-in-Picture**: saca el vídeo en una ventana flotante.

> Nota: AirDrop transfiere archivos o enlaces, no un directo. Por eso se comparte
> el **enlace** del stream, no el vídeo en vivo como archivo (eso no es posible
> desde el navegador).

## Qué hace

- **Reproducir un stream**: pega una URL `.m3u8` y pulsa *Reproducir*.
  Usa [hls.js](https://github.com/video-dev/hls.js) (y reproducción nativa en Safari/iOS).
- **Cargar una playlist M3U**: pega la URL de una lista `.m3u/.m3u8` con varios
  canales y pulsa *Cargar lista*. Se muestran como una lista clicable con logos.
- **Botón "Ejemplo free-tv"**: carga directamente la playlist pública de
  `Free-TV/IPTV` para probar.

## El proxy CORS

Muchos streams bloquean la reproducción directa desde el navegador por CORS.
La app incluye un proxy en `/proxy?url=...` que:

1. Descarga el manifiesto o segmento desde el servidor (no desde el navegador).
2. Reescribe las URLs internas del `.m3u8` para que los segmentos también
   pasen por el proxy.
3. Añade las cabeceras CORS necesarias.

La casilla **"Usar proxy"** está activada por defecto. Si un stream ya permite
CORS, puedes desactivarla para reproducir directamente.

## Estructura

```
m3u8-viewer/
├── package.json
├── server.js         # servidor HTTP + proxy (Node puro)
├── public/
│   └── index.html    # interfaz + reproductor hls.js
└── README.md
```

## Nota legal

Reproduce únicamente contenido para el que tengas derechos o que sea de acceso
libre. Las listas IPTV públicas pueden contener enlaces que caduquen o que no
estén disponibles en tu región.
