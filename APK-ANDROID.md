# App para celular (APK de Android)

El sistema ya es **instalable**: tiene `manifest.json`, iconos y un service worker
(`frontend/public/`). Con eso hay dos maneras de tenerlo en el celular. Las dos abren
**el mismo sistema desplegado en Coolify**, así que todo lo que cambies en la web se ve en el
celular sin volver a instalar nada.

> Requisito para las dos: el sistema tiene que estar desplegado con **https** (un dominio en
> Coolify, por ejemplo `https://bibliotecaorduna.hellosaurio.com`). Con `http://IP` no se puede
> instalar ni usar la cámara.

---

## Opción A. Instalar desde Chrome (1 minuto, sin APK)

1. En el celular abre el sitio en **Chrome**.
2. Menú **⋮** → **Instalar app** (o "Agregar a la pantalla principal").
3. Aparece el icono **Biblioteca** en el celular. Se abre a pantalla completa, sin la barra
   del navegador, y la cámara del escáner funciona.

Sirve para el personal y para los lectores. Para tenerlo así no hace falta ningún archivo.

---

## Opción B. Generar el archivo APK (para pasarlo o subirlo a Play Store)

Se hace con **PWABuilder** (gratis, de Microsoft). Empaqueta el sitio como app Android
(una *Trusted Web Activity*, que usa el Chrome del propio celular, así que la cámara y el
escáner funcionan igual).

1. Entra a **https://www.pwabuilder.com** y pega la URL del sistema
   (`https://bibliotecaorduna.hellosaurio.com`) → **Start**.
2. Te muestra la nota del manifest: debe salir con nombre, iconos y service worker.
3. **Package For Stores** → **Android** → **Generate Package**.
   - Package ID: `com.hellosaurio.bibliotecaorduna`
   - App name: `Biblioteca Orduña`
   - Signing key: **"Create new"** (la primera vez). **Guarda el `.zip` que descargas**:
     trae la llave de firma (`signing.keystore` + `signing-key-info.txt`). Sin esa llave
     no podrás sacar actualizaciones de la misma app.
4. Dentro del `.zip` vienen:
   - `app-release-signed.apk` → **el APK para instalar directo** en los celulares.
   - `app-release-bundle.aab` → el que se sube a Google Play (si algún día lo publicas).
   - `assetlinks.json` → el paso 5.
5. **Para que se abra sin barra de navegador**, copia ese `assetlinks.json` a:

   ```
   biblioteca-orduna/frontend/public/.well-known/assetlinks.json
   ```

   Haz `git push`; Coolify vuelve a desplegar y queda en
   `https://TU-DOMINIO/.well-known/assetlinks.json` (el backend ya lo sirve: es un GET
   público fuera de `/api/`). Comprueba que abre en el navegador.
6. **Instalar el APK en un celular**: pásalo por WhatsApp, Drive o cable. Al abrirlo,
   Android pide permitir "Instalar apps desconocidas" para esa app (WhatsApp, Archivos…).
   Acepta e instala.

### Actualizar la app
- Si solo cambias el sistema (código web): **no hay que hacer nada**. El celular carga la
  versión nueva desde el servidor.
- Solo regeneras el APK si cambias el nombre, el icono o el dominio. Usa **la misma llave**
  del paso 3 (PWABuilder → "Use mine" → sube `signing.keystore`).

---

## ¿Y React Native? (regla 15 de AGENTS.md)

El APK de PWABuilder es el mismo sistema web dentro de una app. Para el día a día alcanza
(escaneo con cámara, préstamos, reservas, todo). La app nativa en React Native que pide la
regla 15 (modo mostrador del personal) sigue pendiente para el final, cuando la web esté
cerrada. Es otro proyecto, con su propio código.
