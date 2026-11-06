# Sistema de Biblioteca Orduña — Cómo ejecutar

```
biblioteca-orduna/
├── pom.xml, src/      ← BACKEND (Spring Boot, puerto 8080)
├── .env.example       ← PLANTILLA de los secretos (copiala a .env)
└── frontend/          ← FRONTEND (React + Vite, puerto 5173)
```

## 1. Base de datos (una sola vez)
```sql
CREATE DATABASE biblioteca_orduna;
```
Usuario/clave en `src/main/resources/application.properties` (root / denis123).
Si usas tu propio `.env`, ahí puedes cambiarlos con `DB_USERNAME` y `DB_PASSWORD`.

## 2. Secretos (una sola vez)
```
copy .env.example .env
```
Rellena el `.env` con tus claves de **Cloudflare R2** (portadas, PDFs y
comprobantes de Yape). El `.env` no se sube al repositorio: está en `.gitignore`.
El backend arranca igual sin él, pero las portadas, los PDFs y los comprobantes
no funcionan hasta que las claves estén puestas.

**No hace falta ninguna clave de Mercado Pago**: el pago en línea ya no existe.
El Yape se paga con el QR estático de Yape Negocio (`src/main/resources/qr.jpeg`)
y el comprobante lo aprueba el personal.

Detalle de cada clave: `LEEME.md` → "Secretos (Cloudflare R2)".

## 2.b Configurar el almacenamiento de archivos (una sola vez)

Con las claves ya puestas en el `.env`, arranca el backend y entra como **gerente**
en **Configuración → Almacenamiento**. Ahí sale el estado de los dos buckets y dos
botones:

- **"Preparar almacenamiento"**: crea en tu cuenta de R2 los buckets que falten,
  con las mismas claves que usa la aplicación (no hay que meterse al panel de
  Cloudflare).
- **"Probar escritura"**: sube un archivo vacío de prueba (0 bytes) para confirmar
  que las portadas y los PDF se pueden guardar.

| Bucket | Acceso | Para qué |
|---|---|---|
| `biblioteca` | público (activar "Public Development URL") | las portadas |
| `biblioteca-pdfs` | privado | los PDF y los comprobantes de Yape |

**Solo queda un paso en el panel de Cloudflare**: activar la **Public Development
URL** del bucket de portadas (Cloudflare → R2 → bucket `biblioteca` → Settings →
Public Development URL → Enable) y copiar esa URL (`https://pub-....r2.dev`) al
`.env` como `R2_URL_PUBLICO_PORTADAS`. Sin ella la portada se guarda pero el
navegador no la muestra.

Después de tocar el `.env`, **reinicia el backend** (el `.env` se lee al arrancar).

## 3. Backend (terminal 1, en la carpeta biblioteca-orduna)
```
mvn spring-boot:run
```
o Run en `BibliotecaApplication` desde IntelliJ / VS Code.
Prueba: http://localhost:8080/ debe mostrar `"estado": "funcionando"`.

## 4. Frontend (terminal 2, en la carpeta biblioteca-orduna/frontend)
```
npm install      (solo la primera vez)
npm run dev
```
Abre http://localhost:5173

## Usuarios de prueba
| Rol | Correo | Contraseña |
|---|---|---|
| Gerente | admin@biblioteca.pe | admin123 |
| Bibliotecario | biblio@biblioteca.pe | biblio123 |
| Lector | lector@biblioteca.pe | lector123 |

## 5. Probar el flujo de libros virtuales (de punta a punta)
Con el backend encendido y el `.env` completo:
```
./prueba-libros-virtuales.sh
```
El script abre la **caja**, crea un libro virtual con su PDF, lo pide el lector
pagando en la tienda, el personal lo cobra con el código del cliente, repite el
tramo con **Yape** (el lector sube la captura y el personal la aprueba),
comprueba el aviso de "felicidades", la URL firmada del PDF, los **movimientos de
la caja** y su arqueo, y que el libro vencido sale "en plomo".
Deja dos libros de prueba en el catálogo (se pueden borrar desde el panel).

## 5.b Probar el pago con Yape (QR + comprobante)
1. El lector pide un libro virtual eligiendo **Yape**. Se abre el modal con el
   QR de Yape Negocio y el **monto escrito debajo**.
2. Paga en la app de Yape (teclea el monto a mano: el QR es estático), saca una
   **captura de pantalla** y la sube como comprobante.
3. El personal recibe un aviso y, en "Reservas de libro virtual" → "Comprobantes
   Yape", la ve (URL firmada de 15 minutos) y la **aprueba** o la **deniega**.
4. Al aprobarla, el lector tiene el PDF y recibe el aviso de "felicidades,
   compra exitosa". Al denegarla, ve el motivo y puede subir otro comprobante.

## 5.c Abrir la caja (obligatorio para cobrar)
El dinero **solo** entra al cajón con una caja abierta:
1. Entra como bibliotecario o gerente → sección **Caja** → "Abrir caja" →
   escribe el **monto inicial** (el dinero que ya había en el cajón).
2. Al devolver un préstamo se cobra el precio de la copia y el total sube.
3. Al cerrar la caja se cuenta el efectivo y el sistema dice si sobra o falta.
4. Con **dos cajas abiertas a la vez** la segunda falla (400).

## 5.d Ver la auditoría (solo el gerente)
Entra como **gerente** → sección **Auditoría**: qué hizo cada persona del
personal, con fecha y hora. Se puede filtrar por fechas, por persona y por tipo
de acción. Con el bibliotecario la pantalla no se ve (lo manda a Inicio) y la
API responde 403.

## 6. Escanear códigos de barras (ISBN y código de inventario)

- **En la PC**: el lector de códigos de barras actúa como teclado. Pásalo sobre
  el ISBN (o sobre la etiqueta del ejemplar) y el campo se rellena solo.
- **En el celular**: en el mismo formulario, el botón 📷 abre la cámara y lee el
  código. Se usa la API nativa del navegador, sin instalar nada.

Dos avisos:
- La cámara solo se abre en **https** o en **localhost**. En el VPS (que se
  entra por IP, `http://`) todavía no se puede usar: ahí va el lector físico.
- **Firefox** no soporta esa API: el botón avisa y se escribe el código a mano.

Si al escanear el ISBN sale "la ruta no existe", es que el backend sigue con la
versión vieja: reinícialo (`Ctrl+C` y `mvn spring-boot:run`).

## Nota
- El backend usa `ddl-auto=update`: crea las tablas nuevas y **añade** las
  columnas nuevas a las que ya existen (como `ejemplares.precio` o
  `prestamos.precio_copia`), pero **no** cambia ni borra columnas existentes. Si
  cambiaste el tipo o el nombre de una columna, borra la base a mano.
- CORS: el backend permite el 5173 y los orígenes que digas en
  `CORS_ALLOWED_ORIGINS` (en el `.env`).

Detalle del backend: `LEEME.md` · Detalle del frontend: `frontend/README.md`
