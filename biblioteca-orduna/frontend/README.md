# Frontend — Biblioteca Orduña

React 18 + Vite + React Router + Axios. Réplica de las 14 pantallas del prototipo Figma
(documento *Seguimiento 2*), **conectada al backend Spring Boot** (`/api`, JWT).

## Cómo correrlo

1. Arranca el backend (Spring Boot en `http://localhost:8080`).
2. En esta carpeta:
   ```bash
   npm install
   npm run dev
   ```
3. Abre http://localhost:5173

| Rol | Correo | Contraseña |
|---|---|---|
| Gerente | admin@biblioteca.pe | admin123 |
| Bibliotecario | biblio@biblioteca.pe | biblio123 |
| Lector | lector@biblioteca.pe | lector123 |

Si el backend corre en otra dirección, cambia `VITE_API_URL` en el archivo `.env`
(y reinicia `npm run dev`). El backend ya permite CORS para `http://localhost:5173`.

## Cómo funciona la conexión

- `src/api/axiosClient.js` — cliente único: agrega `Authorization: Bearer <token>` a cada
  petición; si el backend responde 401 (token vencido) cierra la sesión y vuelve al login.
  `mensajeError()` muestra el `mensaje` / `errores` que manda el `GlobalExceptionHandler`.
- `src/context/AuthContext.jsx` — login real con `POST /api/auth/login`; guarda token y rol
  ("Recordar sesión" = `localStorage`, si no `sessionStorage`).
- `src/context/RealtimeContext.jsx` — abre `GET /api/eventos?token=...` (SSE) y reparte los
  eventos. `useRefrescoTiempoReal(recursos, recargar)` en `hooks.js` recarga cada pantalla
  cuando cambia algo que le afecta, y la campanita se actualiza con los eventos de aviso.
- `src/api/*.js` — una función por endpoint del backend.
- `src/hooks.js` — `useApi` (cargar datos), `useAccion` (guardar con mensaje de éxito/error)
  y `useRefrescoTiempoReal` (recarga automática por SSE).

## Pantallas y endpoints

| Pantalla | Ruta | Rol | Endpoints |
|---|---|---|---|
| Login / Registro | `/login`, `/registro` | público | `POST /auth/login`, `POST /usuarios/registro` |
| Inicio | `/inicio` | todos | `GET /dashboard` (personal) · `GET /mi/panel` (lector) |
| Libros | `/libros` | todos | `GET /libros`, `/libros/buscar`, `POST/PUT /libros`, `GET/POST /generos`, `POST /reservas` (lector) |
| Alta de libro (escaner de ISBN) | dentro de `/libros` | personal | `GET /libros/isbn/{isbn}`, `POST /libros/completo`, `PUT /libros/{id}/completo`, `POST /libros/{id}/portada`, `POST /libros/{id}/pdf` |
| Reservas de libro virtual | `/libros-virtuales` | personal | `GET /accesos-virtuales/pedidos`, `GET /accesos-virtuales/codigo/{codigo}`, `POST /accesos-virtuales/{id}/cambiar`, `POST /accesos-virtuales/{id}/cobrar`, `GET /accesos-virtuales/libro/{idLibro}`, `GET /comprobantes/pendientes`, `GET /comprobantes/{id}/imagen`, `POST /comprobantes/{id}/aprobar`, `POST /comprobantes/{id}/denegar` |
| Mis libros virtuales | `/mis-libros-virtuales` | todos | `GET /accesos-virtuales/mis-libros`, `GET /accesos-virtuales/{id}/pdf`, `POST /accesos-virtuales/{id}/comprobante`, `GET /accesos-virtuales/{id}/comprobantes`, `POST /accesos-virtuales` (lector) |
| Autores | `/autores` | todos | `GET/POST/PUT/DELETE /autores`, `GET /autores/{id}/libros` |
| Usuarios | `/usuarios` | personal | `GET /usuarios` (paginado), `POST/PUT /usuarios`, `POST /usuarios/{id}/suspender` |
| Préstamos | `/prestamos` | personal | asistente: `GET /usuarios`, `GET /libros`, `POST /prestamos` |
| Mis préstamos y multas | `/prestamos` | lector | `GET /mi/prestamos`, `GET /mi/multas` |
| Devoluciones | `/devoluciones` | personal | `GET /prestamos?estado=`, `POST /devoluciones` |
| Reservas | `/reservas` | personal | `GET /reservas`, `GET /reservas/cola/{id}`, `PATCH /reservas/{id}/cancelar` |
| Mis reservas | `/reservas` | lector | `GET /mi/reservas`, `POST /reservas`, `PATCH /reservas/{id}/cancelar` |
| Ejemplares | `/ejemplares` | personal | `GET /ejemplares`, `POST /libros/{id}/ejemplares`, `PUT /ejemplares/{id}` |
| Pagos de multas | `/pagos-multa` | personal | `GET /multas`, `POST /multas/{id}/pagar`, `GET /multas/pagos` |
| Caja | `/caja` | personal | `GET /cajas/actual`, `POST /cajas`, `POST /cajas/{id}/cerrar`, `GET /cajas`, `GET /cajas/{id}`, `GET /cajas/pendientes` |
| Sanciones y bloqueos | `/sanciones` | personal | `GET /sanciones`, `POST /usuarios/{id}/suspender`, `PATCH /sanciones/{id}/levantar` |
| Avisos | `/avisos` | todos | `GET /notificaciones`, `PATCH .../leida`, `POST /notificaciones`, `GET /notificaciones/todas`, `PUT /configuracion` (interruptores) |
| Bitácora de turnos | `/bitacora` | personal | `GET /turnos`, `GET /turnos/actual`, `POST /turnos/abrir`, `POST /turnos/cerrar` |
| Reportes y cierre | `/reportes` | personal | `GET /reportes/*`, descarga `GET /reportes/*/csv`, `GET/POST /cierre-diario` |
| Configuración | `/configuracion` | gerente | `GET/PUT /configuracion`, `GET /configuracion/historial`, `GET /almacenamiento/estado`, `POST /almacenamiento/probar-escritura`, `POST /almacenamiento/preparar` |
| Auditoría | `/auditoria` | gerente | `GET /auditoria` (paginado, con filtros de fechas, persona y acción) |
| Mi perfil | `/perfil` | todos | `GET/PUT /usuarios/me` |

*Personal* = bibliotecario o gerente (bloque "Administración" del menú). El lector ve un menú
reducido: Inicio, Libros, Autores, Mis préstamos y multas, Mis reservas, Mis libros virtuales
y Avisos.

## Libros virtuales (portadas, PDF, alquiler y compra)

- **Alta** (`pages/Libros/FormLibro.jsx`): escanea el ISBN (el backend busca el libro en
  Google Books u Open Library y devuelve la portada), elige el tipo (físico / virtual /
  híbrido) y pone los precios. El formulario registra **solo la obra original**: las
  **copias (ejemplares)** se dan de alta aparte, en el módulo **Ejemplares**. La portada va
  al bucket público de R2 y el PDF al privado: todo en un solo `multipart`.
- **Portada** (`components/common/Portada.jsx`): usa la imagen real (`url_imagen`) y, si
  todavía no hay o la imagen no carga, cae en la portada generada de siempre.
- **Pedido** (`pages/Libros/PedidoVirtual.jsx`): el lector elige alquiler o compra y cómo
  pagar. Con **Yape** se abre el modal del QR (`components/common/QrYape.jsx`): el QR
  estático de Yape Negocio con el **monto escrito debajo**, un campo opcional para el
  código de operación y el botón de subir la **captura** del pago. En efectivo recibe un
  número de cola y un **código de 24 horas** para el mostrador.
- **Mostrador** (`pages/LibrosVirtuales/LibrosVirtuales.jsx`): se busca la orden con el
  código del cliente y se decide: **cancelar**, **cambiar** o **cobrar**. También se ve
  quién compró o alquiló cada libro, y en la sección **"Comprobantes Yape"** los que
  esperan revisión: **ver la captura**, **aceptar** o **denegar** (el denegar pide el
  motivo, que ve el lector).
- **Perfil** (`pages/LibrosVirtuales/MisLibrosVirtuales.jsx`): los libros del lector, con
  los días que le quedan. Los alquileres vencidos salen **en plomo** y hay que volver a
  reservarlos. Un pedido con comprobante en revisión sale como *"Comprobante en
  revisión"* (es un estado derivado, no un estado nuevo del pedido).

## Pago con Yape (QR + comprobante)

No hay API pública de Yape con QR dinámico y monto exacto: el QR que se muestra es el
**estático de Yape Negocio** (`src/main/resources/qr.jpeg`, servido por
`GET /api/pagos/yape/qr`), con el monto escrito debajo. El lector paga en su app
tecleando ese monto, saca una captura y la sube; el personal la **aprueba** (el pedido
se paga y el lector recibe el *"felicidades, compra exitosa"*) o la **deniega** (el lector
ve el motivo y puede subir otro comprobante).

La captura se guarda en el bucket **privado** de R2 y se entrega con una URL firmada de
15 minutos. **No hay checkout de tarjeta**: Mercado Pago ya no se usa.

## Caja (el cajón de dinero)

`pages/Caja/Caja.jsx` tiene tres pestañas:

- **Hoy**: si no hay caja abierta, el formulario para abrirla (con el **monto inicial**);
  con caja abierta, el monto inicial, lo cobrado, el total del día, el desglose por
  método de pago y por tipo de cobro, los movimientos y el botón **"Cerrar caja"** (pide
  el efectivo contado y dice si sobra o falta).
- **Pendientes**: los préstamos sin devolver con su precio a cobrar, las reservas por
  recoger con su fecha límite, las que están en cola y los pedidos virtuales sin cobrar.
- **Historial**: las cajas cerradas, con su arqueo, y el detalle de sus movimientos.

El dinero **solo** entra por los movimientos: sin caja abierta, el backend rechaza el
cobro (400) y la pantalla de devoluciones avisa antes de dejar confirmar.

## Auditoría (quién hizo qué)

`pages/Auditoria/Auditoria.jsx` es **solo del gerente**. Lista las acciones del personal
con fecha y hora, y se filtra por fechas, por persona y por tipo de acción. Los colores y
las etiquetas de cada acción están en `utils/estados.js` (`tipoAccionAuditoria`).

## Almacenamiento de archivos (Cloudflare R2)

La pestaña **Almacenamiento** de `pages/Configuracion/Configuracion.jsx` (solo gerente) dice
si el almacenamiento está listo: si hay credenciales en el `.env`, si los dos buckets
(`biblioteca` para portadas, `biblioteca-pdfs` para los PDF) existen y se pueden usar, y
qué URL pública se está usando para las portadas.

- **"Preparar almacenamiento"** (`POST /api/almacenamiento/preparar`) crea en R2 los
  buckets que falten, con las mismas credenciales que usa la aplicación.
- **"Probar escritura"** (`POST /api/almacenamiento/probar-escritura`) sube un archivo
  vacío de prueba (0 bytes) para confirmar que se pueden guardar portadas y PDF.

Lo único que hay que activar a mano en Cloudflare es la *Public Development URL* del
bucket de portadas y copiarla al `.env` como `R2_URL_PUBLICO_PORTADAS`. Ver
`COMO-EJECUTAR.md` §2.b.

## Copias (ejemplares) y precio por copia

- El formulario de libro registra **solo la obra original** (ficha, autores, géneros, tipo,
  precios, portada y PDF). **Las copias no se crean ahí.**
- Un libro con 5 ejemplares es un libro con 5 filas agrupadas. Solo los ejemplares se
  reservan y se prestan.
- Cada copia puede tener **su propio precio** (`precio`): una edición antigua o un tiraje
  raro. Si no se indica, se usa el precio general del libro.
- Dónde se dan de alta las copias:
  - desde el módulo **Ejemplares** → "+ Registrar ejemplar" (con el botón 📷 para escanear el
    código de inventario);
  - desde la ficha del libro → **"Agregar o editar copias"**, que abre `Ejemplares` con el
    libro ya elegido.
- Un libro puede registrarse **sin ninguna copia**: sin ejemplares no se presta (se
  reserva), pero la obra queda en el catálogo. Los libros **virtuales** no admiten
  ejemplares: sus copias son el PDF.
- Al prestar, el precio de la copia **se copia al préstamo** (`precio_copia`), en las dos
  rutas (fecha fija y abierto): cambiar el precio después no altera los préstamos ya
  hechos. Sale en la confirmación del préstamo y en la tabla de devoluciones.

## Escáner de códigos (cámara y lector físico)

`components/common/EscanerCodigo.jsx` sirve para los tres casos: el ISBN de un libro nuevo,
el código de inventario de una copia y el código de una etiqueta.

- **En la PC**: el lector de códigos de barras actúa como teclado. Se pasa el código y se
  pulsa Enter; el campo se rellena solo.
- **En el celular**: el botón 📷 abre la cámara y lee el código.

Usa la API nativa del navegador (`window.BarcodeDetector` + `getUserMedia`): **no se añade
ninguna librería**. Dos límites:

- hace falta un **contexto seguro** (https o localhost). En el VPS, que se entra por IP
  (http://), la cámara todavía no se puede abrir: ahí se usa el lector físico o se escribe
  el código a mano;
- **Firefox** no implementa `BarcodeDetector`: el componente avisa y se sigue escribiendo
  a mano.

Si el backend responde 404 al escanear el ISBN, es que está corriendo la versión vieja:
reinícialo (`Ctrl+C` y `mvn spring-boot:run`).

## Estructura

```
src/
├── api/            una función por endpoint (axios)
├── components/
│   ├── common/     Badge, Estado, Modal, Campo, Paginacion, Portada, StatCard, SearchInput, Cargando, EscanerCodigo, QrYape
│   └── layout/     AppLayout, Sidebar (menú por rol), Topbar (buscador, campanita, usuario)
├── context/        AuthContext (sesión JWT), RealtimeContext (SSE), ToastContext (mensajes)
├── pages/          una carpeta por pantalla
├── routes/         rutas y protección por rol
├── styles/         global.css (colores y tipografía del Figma)
├── utils/          formatos (fechas, soles), estados → colores, sesión
└── hooks.js        useApi, useAccion, useDebounce, useRefrescoTiempoReal
```

## Producción

```bash
npm run build     # genera dist/
```
