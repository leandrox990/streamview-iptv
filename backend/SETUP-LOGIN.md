# Configurar el login (Supabase)

Con esto tu app pedirá usuario y contraseña, y cada cuenta solo podrá usarse en
**un dispositivo a la vez** (si alguien entra en otro, el primero se desconecta).
Tú creas las cuentas desde el panel de administración y se las pasas a tus conocidos.

## 1. Crear el proyecto Supabase (gratis)

1. Entra en https://supabase.com y crea una cuenta.
2. **New project** → ponle un nombre y una contraseña de base de datos → **Create**.
3. Espera 1–2 minutos a que se aprovisione.

## 2. Cargar la base de datos

1. En el proyecto, ve a **SQL Editor → New query**.
2. Abre el archivo `backend/supabase_schema.sql`, copia **todo** su contenido, pégalo y pulsa **Run**.
3. Debe decir "Success". Esto crea las tablas y las funciones de login.

## 3. Obtener tus claves

En **Project Settings → API** copia:

- **Project URL** → algo como `https://xxxxx.supabase.co`
- **anon public** key → una cadena larga que empieza por `eyJ...`

(La anon key es pública y segura de usar en la app; la seguridad está en las
reglas del servidor.)

## 4. Cambiar el token de administrador

El token por defecto es **`cambiame`**. Cámbialo cuanto antes:
- Opción fácil: en el panel admin (paso 5) usa "Cambiar token de administrador".
- O en SQL Editor:
  ```sql
  update app_config set value = crypt('TU_NUEVO_TOKEN', gen_salt('bf')) where key='admin_token';
  ```

## 5. Abrir el panel de administración

1. Abre el archivo `backend/admin.html` (doble clic; se abre en el navegador).
2. Pega la **URL**, la **anon key** y tu **token admin** → **Guardar credenciales** → **Entrar**.
3. Desde ahí puedes: **crear usuarios**, activar/desactivar, poner fecha de
   vencimiento, cambiar contraseña, **expulsar** una sesión, y fijar la **versión** de la app.

> Consejo: puedes subir `admin.html` a un hosting gratis (Netlify, GitHub Pages)
> para administrarlo desde cualquier lado, o simplemente abrirlo en tu PC.

## 6. Activar el login en la app

En el archivo del reproductor, pon tus claves en estas dos líneas
(están al inicio del `<script>`):

```js
const SUPABASE_URL  = "https://xxxxx.supabase.co";
const SUPABASE_ANON = "eyJ...";
```

- Para la **web/desktop**: edítalo en `public/index.html`.
- Para el **APK (Fire TV)**: edítalo en `android/app/src/main/assets/index.html`.

Si me pasas la URL y la anon key, yo las dejo puestas en ambos y recompilo el APK.
Mientras estén vacías, la app funciona **sin** login (abierta).

## 7. Indicador de versión

En el panel admin, sección "Versión de la app", pon la última versión (ej. `1.1.0`).
Si es distinta a la que tiene la app instalada, en el menú aparecerá
"actualización disponible" en dorado. La versión actual de la app es `APP_VERSION`
(la constante junto a las claves).

---

### Cómo funciona el "un solo dispositivo"
Al iniciar sesión, el servidor guarda esa sesión como la única válida. La app
revisa cada ~25 s que su sesión siga siendo la vigente; si alguien inició sesión
con la misma cuenta en otro equipo, la comprobación falla y el dispositivo viejo
se bloquea con el aviso "Se inició sesión en otro dispositivo".
