# Despliegue en Coolify (VPS 5.189.143.53)

Dos recursos dentro del mismo proyecto y entorno de Coolify, igual que "Diamantes":

| Recurso | Tipo | Qué es |
|---|---|---|
| `biblioteca` | Application (Dockerfile, desde GitHub) | Todo el sistema: backend Spring Boot **+ frontend React ya compilado**, en un solo contenedor (puerto 8080) |
| `biblioteca-db` | Database → MySQL 8 | La base de datos `biblioteca_orduna` |

```
Navegador ──https──> Traefik (Coolify) ──> biblioteca:8080 ──red interna──> biblioteca-db:3306
                                               │
                                               └──> Cloudflare R2 (portadas y PDF)
```

El frontend y la API comparten dominio: el navegador pide `/api/...` al mismo sitio, así que
no hay CORS que configurar. Las tablas las crea Hibernate al arrancar y `data.sql` carga
los usuarios de prueba, la configuración y los géneros.

---

## 1. Subir el proyecto a GitHub

Desde la carpeta `biblioteca-orduna-completo` (donde está `Dockerfile`):

```bash
git add .
git commit -m "Despliegue con Docker para Coolify"
git branch -M main
git remote add origin https://github.com/Sebas123ZS/biblioteca-orduna.git   # si no existe
git push -u origin main
```

Revisa en GitHub que **no** aparezca ningún `.env` (están en `.gitignore`). Si alguno se
subió alguna vez, cambia esas claves.

## 2. Crear la base de datos (`biblioteca-db`)

1. Proyecto → entorno `production` → **+ New Resource** → **Databases** → **MySQL**.
2. Servidor: `localhost` (el mismo servidor de la app).
3. Antes de arrancarla, en **General**:
   - Name: `biblioteca-db`
   - **Initial Database**: `biblioteca_orduna`
   - **Normal User**: `biblioteca` (deja la contraseña que genera Coolify, o pon una larga)
   - Root password: la que genera Coolify
   - Custom MySQL configuration (opcional pero recomendado):
     ```
     [mysqld]
     character-set-server=utf8mb4
     collation-server=utf8mb4_unicode_ci
     default-time-zone='-05:00'
     ```
4. **No** actives "Make it publicly available": solo la app necesita llegar a la base.
5. **Start**.
6. Copia la **MySQL URL (internal)**. Tiene esta forma:
   `mysql://biblioteca:CONTRASEÑA@HOST_INTERNO:3306/biblioteca_orduna`
   El `HOST_INTERNO` (un código tipo `q8k4w0c...`) es lo que va en `DB_URL` en el paso 3.
7. En **Backups**, programa una copia diaria.

## 3. Crear la aplicación (`biblioteca`)

1. **+ New Resource** → **Application** → **Private Repository (with GitHub App)**. Si el
   repositorio es público, también sirve "Public Repository" con la URL.
2. Repositorio `biblioteca-orduna`, rama `main`.
3. **Build Pack: Dockerfile**
   - Base Directory: `/`
   - Dockerfile Location: `/Dockerfile`
4. **Ports Exposes**: `8080`
5. **Domains** (elige una opción):
   - Con un dominio tuyo (recomendado): crea un registro **A** `biblioteca.hellosaurio.com → 5.189.143.53`
     y pon `https://biblioteca.hellosaurio.com`. Coolify saca el certificado HTTPS solo.
   - Sin dominio: `https://biblioteca.5.189.143.53.sslip.io` (sslip.io apunta a tu IP gratis).
   > **La cámara del escáner solo funciona con HTTPS.** Por IP sola (`http://5.189.143.53`) se
   > puede usar el lector físico, pero no la cámara del celular.
6. **Health check**: path `/api/salud`, puerto `8080` (el Dockerfile ya trae el suyo).
7. **Environment Variables**: pega esto en **Developer view** y completa lo que está entre `< >`.
   Ninguna es "Build Variable": todas son de ejecución.

```env
# --- Base de datos (el HOST_INTERNO sale de la MySQL URL internal del paso 2) ---
DB_URL=jdbc:mysql://<HOST_INTERNO>:3306/biblioteca_orduna?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8
DB_USERNAME=biblioteca
DB_PASSWORD=<contraseña del usuario biblioteca>

# --- Seguridad ---
# 64+ caracteres al azar. Genera una con:  openssl rand -base64 48
JWT_SECRET=<clave larga al azar>
SHOW_SQL=false

# --- Cloudflare R2 (los mismos valores de tu .env) ---
R2_ACCOUNT_ID=<tu account id>
R2_ACCESS_KEY_ID=<tu access key>
R2_SECRET_ACCESS_KEY=<tu secret key>
R2_BUCKET_PORTADAS=biblioteca
R2_BUCKET_PDFS=biblioteca-pdfs
R2_URL_PUBLICO_PORTADAS=https://pub-4dfe0114a0ae4a4087eea497277d437a.r2.dev
R2_DOMINIO_PUBLICO=

# --- Direcciones (el dominio que pusiste en el punto 5) ---
FRONTEND_URL=https://biblioteca.hellosaurio.com
CORS_ALLOWED_ORIGINS=https://biblioteca.hellosaurio.com

# --- Escáner de ISBN (opcional, recomendado en el VPS) ---
GOOGLE_BOOKS_API_KEY=
```

8. **Deploy**. El primer build tarda más (descarga Node y las dependencias de Maven); los
   siguientes reutilizan la caché.
9. En **Logs** busca:
   - `Started BibliotecaApplication` → arrancó.
   - `Almacenamiento R2 -> credenciales: OK | portadas: LISTO | pdfs: LISTO` → R2 listo.
10. Abre el dominio y entra con `admin@biblioteca.pe` / `admin123`.

## 4. Después del primer arranque (no lo saltes)

- **Cambia las contraseñas de prueba** (`admin123`, `biblio123`, `lector123`): en
  producción cualquiera las conoce porque están en `data.sql`.
- Configuración → Almacenamiento → **Preparar almacenamiento** (por si falta `biblioteca-pdfs`)
  y después **Probar escritura**.
- Caja → **abre la caja** antes de cobrar (si no, devolver un libro da 400).
- Activa **Automatic deployment** (Webhooks / GitHub App): cada `git push` a `main` vuelve
  a desplegar.

## 5. Clave de Google Books (para que el escáner no falle en el VPS)

Sin clave, Google Books reparte un cupo diario **por IP** y desde un VPS se agota rápido
(responde 429 y el escáner dice que no encuentra el libro aunque exista). La clave es gratis:

1. https://console.cloud.google.com → crea un proyecto.
2. **APIs y servicios → Biblioteca → Books API → Habilitar**.
3. **Credenciales → Crear credenciales → Clave de API**. Restríngela a "Books API".
4. Pégala en `GOOGLE_BOOKS_API_KEY` y vuelve a desplegar.

## 6. Problemas típicos

| Lo que ves en los logs | Causa | Arreglo |
|---|---|---|
| `Communications link failure` | `DB_URL` con el host equivocado, o la base en otro servidor o red | Copia otra vez el host de la **MySQL URL (internal)**; los dos recursos tienen que estar en el mismo servidor |
| `Public Key Retrieval is not allowed` | Falta `allowPublicKeyRetrieval=true` | Usa el `DB_URL` completo de arriba |
| `Access denied for user` | Usuario o contraseña distintos a los de `biblioteca-db` | Revisa `DB_USERNAME` / `DB_PASSWORD` |
| 502 / Bad Gateway en el navegador | El puerto expuesto no es 8080, o la app todavía arranca | **Ports Exposes = 8080**; espera ~1 min al primer arranque |
| Escáner: "ningún catálogo reconoce el ISBN" con libros que existen | Cupo de Google agotado | Pon `GOOGLE_BOOKS_API_KEY` |
| La cámara no abre | El sitio está en `http://` | Usa un dominio con `https://` (punto 3.5) |
| `OutOfMemoryError` | VPS con poca RAM | En Coolify → Resource Limits, da al menos 768 MB a la app |

## 7. App para el celular (APK)

Cuando el sistema ya esté con https, sigue [`APK-ANDROID.md`](./APK-ANDROID.md): se instala
desde Chrome en 1 minuto o se genera el APK con PWABuilder.

## 8. Probar la imagen en tu PC antes de subirla (opcional)

Necesitas Docker Desktop:

```bash
docker compose -f docker-compose.local.yml up --build
# abre http://localhost:8080
```

Levanta un MySQL de prueba y la app con tu `biblioteca-orduna/.env` (R2).
