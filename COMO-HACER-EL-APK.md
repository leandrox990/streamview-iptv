# Cómo generar el APK e instalarlo en Smart TV / Fire TV Stick

La app Android está en la carpeta `android/`. Es un **WebView** que empaqueta la
interfaz (funciona sin el servidor Node: reproduce los streams directamente).

No se pudo compilar automáticamente porque el entorno es ARM64 y las herramientas
de Android solo compilan en x86_64. Aquí tienes dos formas fáciles de obtener el APK.

---

## Opción A — En la nube con GitHub Actions (recomendada, sin instalar nada)

1. Crea una cuenta gratis en https://github.com y un **repositorio nuevo** (vacío).
2. Sube **todo el contenido de la carpeta `m3u8-viewer`** al repositorio
   (incluida la carpeta oculta `.github`). Puedes arrastrar los archivos en
   "Add file → Upload files".
3. Ve a la pestaña **Actions** del repositorio. El flujo "Build APK" se ejecuta
   solo (o pulsa "Run workflow").
4. Cuando termine (2–3 min), entra en la ejecución y descarga el artefacto
   **StreamView-APK**. Dentro está `app-debug.apk`.

---

## Opción B — Con Android Studio (en tu ordenador)

1. Instala Android Studio (https://developer.android.com/studio).
2. **Abre la carpeta `android/`** (Open → seleccionar `android`).
3. Espera a que sincronice Gradle. Luego menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## Instalar el APK en el Fire TV Stick

1. En el Firestick: **Ajustes → My Fire TV → Opciones de desarrollador** y activa
   **Apps de fuentes desconocidas** (para la app que uses para instalar).
2. Instala la app gratuita **Downloader** desde la tienda del Firestick.
3. Sube tu `app-debug.apk` a un sitio accesible (por ejemplo Google Drive con
   enlace directo, o Dropbox) y abre ese enlace desde **Downloader**; o usa
   `adb install app-debug.apk` si tienes ADB en tu ordenador
   (`adb connect IP_DEL_FIRESTICK:5555`).
4. Se instalará **StreamView** y aparecerá en tus aplicaciones.

## Instalar en un Smart TV Android (Sony, TCL, Philips…)

Igual que arriba: activa "Fuentes desconocidas", instala **Downloader** (o usa un
USB con el APK y un explorador de archivos) e instala el `app-debug.apk`.

---

### Notas

- El APK es de tipo **debug** (firmado con la clave de depuración). Se instala y
  funciona perfectamente; solo no está preparado para publicarse en tiendas.
- La app necesita **conexión a internet** (los canales se transmiten online).
- Controles con el mando: flechas para moverte, **OK** para reproducir/pantalla
  completa, **← →** en pantalla completa para cambiar de canal, **Atrás** para
  volver a la lista.
