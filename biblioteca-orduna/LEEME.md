# Sistema de Biblioteca Orduña – Backend (Spring Boot + JWT)

Backend completo alineado con el documento *Seguimiento 2*: roles **LECTOR / BIBLIOTECARIO / GERENTE**,
reglas de negocio, 14 pantallas de Figma y tablas con los nombres del modelo (`id_usuario`,
`contrasena_hash`, `libros_autores`, `libros_generos`, `bitacora_turnos`, `configuracion_multas`, …).

## Cómo arrancar

1. En MySQL: `CREATE DATABASE biblioteca_orduna;`
   **Si ya tenías la base de la versión anterior, bórrala y créala de nuevo**
   (`DROP DATABASE biblioteca_orduna; CREATE DATABASE biblioteca_orduna;`): cambiaron roles,
   columnas y nombres de tablas, y `ddl-auto=update` no modifica columnas existentes.
2. Usuario/clave en `src/main/resources/application.properties` (root / denis123).
3. `mvn spring-boot:run` (o Run en `BibliotecaApplication`). Hibernate crea las tablas y
   `data.sql` carga usuarios, configuración y géneros.
4. API en `http://localhost:8080`.
5. Prueba del flujo de libros virtuales de punta a punta (crea el libro con PDF, lo pide
   el lector en efectivo, el personal lo cobra y se comprueba el PDF y el vencimiento):
   `./prueba-libros-virtuales.sh` (necesita el backend arriba y el `.env` con R2).

### Secretos (Cloudflare R2)

Los secretos **no** van en `application.properties` (ese archivo se sube al repositorio).
Van en un `.env` en la carpeta `biblioteca-orduna/`, que Spring lee solo:

```properties
spring.config.import=optional:file:.env[.properties]
```

Claves que necesita el `.env`:

| Variable | Para qué |
|---|---|
| `R2_ACCOUNT_ID` | Cuenta de Cloudflare R2 (aparece en el panel, junto a las claves) |
| `R2_ACCESS_KEY_ID` | Clave de acceso de R2 |
| `R2_SECRET_ACCESS_KEY` | Clave secreta de R2 |
| `R2_BUCKET_PORTADAS` | Bucket **público** de las portadas (ej. `biblioteca`) |
| `R2_BUCKET_PDFS` | Bucket **privado** de los PDF (ej. `biblioteca-pdfs`) |
| `R2_URL_PUBLICO_PORTADAS` | URL pública del bucket de portadas (`https://pub-xxxx.r2.dev`) |
| `R2_DOMINIO_PUBLICO` | Dominio público propio del bucket de portadas (opcional) |
| `FRONTEND_URL` | URL pública del frontend (en producción) |
| `GOOGLE_BOOKS_API_KEY` | Clave gratis de Google Books (opcional; evita el 429 del escáner en el VPS) |
| `DB_URL` · `JWT_SECRET` · `SHOW_SQL` | Solo en producción (Coolify): URL JDBC de MySQL, clave del JWT y `false` para no imprimir el SQL |

Despliegue en el VPS con Docker + Coolify: ver [`../DESPLIEGUE-COOLIFY.md`](../DESPLIEGUE-COOLIFY.md).

No hace falta ninguna clave de Mercado Pago: **el pago en línea ya no usa Mercado Pago**.
El Yape se paga con el QR estático de Yape Negocio y el comprobante lo aprueba el personal.

Si falta alguna, la aplicación **arranca igual**: los libros normales funcionan y
`GET /api/almacenamiento/estado` dice qué falta.

Para dejarlo listo sin ir a mano al panel de Cloudflare, entra como gerente en
**Configuración → Almacenamiento** (solo gerente) y pulsa **"Preparar
almacenamiento"**: crea en R2 los buckets que falten, con las mismas claves que
usa la aplicación. El botón **"Probar escritura"** sube un archivo vacío de
prueba para confirmar que el token puede escribir.

**Portadas sin URL pública:** si `R2_URL_PUBLICO_PORTADAS` (y `R2_DOMINIO_PUBLICO`)
están vacíos, las portadas **se ven igual**: las sirve el backend desde el bucket en
`GET /api/archivos/portadas/{isbn}/{archivo}` (público, con caché de un año en el
navegador, porque cada portada nueva tiene una clave nueva). En la base queda el link
relativo `/api/archivos/portadas/...` y el frontend le pone el host del backend.

Para el VPS conviene que las entregue Cloudflare: activa la **Public Development URL**
del bucket de portadas (R2 → bucket → Settings) y cópiala al `.env` como
`R2_URL_PUBLICO_PORTADAS`. Al reiniciar, **todas** las portadas (también las ya
guardadas) se devuelven con la URL pública: el link se rearma al leerlo
(`AlmacenamientoService.urlVisible`), sin tocar la base. Eso arregla también los links
antiguos con la base "deducida" `https://<bucket>.<cuenta>.r2.dev`, que nunca funcionó.

Claves en R2:
- Portadas: `portadas/{isbn}/{marca-de-tiempo}.{jpg|png|webp}` (bucket público). La
  anterior se borra **solo cuando la transacción se confirma**; si se revierte, se borra
  la nueva.
- PDF: `pdfs/{isbn}/{marca-de-tiempo}.pdf` (bucket privado). Igual que la portada: el viejo
  se borra al confirmar. Se valida el contenido (`%PDF`), no solo la extensión.
- Comprobantes de Yape: `comprobantes/{id}.{ext}` (bucket privado).
El tipo de imagen se reconoce por sus primeros bytes (JPG, PNG o WEBP).

| Rol | Email | Contraseña |
|---|---|---|
| GERENTE | admin@biblioteca.pe | admin123 |
| BIBLIOTECARIO | biblio@biblioteca.pe | biblio123 |
| LECTOR | lector@biblioteca.pe | lector123 |

## Login (RF05)

```http
POST /api/auth/login
{"email":"biblio@biblioteca.pe","password":"biblio123"}
→ 200 {"token":"eyJ...","tipo":"Bearer","expira_en_ms":28800000,
        "id_usuario":2,"nombre":"Bibliotecario Principal","email":"biblio@biblioteca.pe","rol":"BIBLIOTECARIO"}
```

- El frontend guarda `token` y lo envía en cada petición: `Authorization: Bearer <token>` (dura 8 h).
- Según `rol`, redirige al Inicio que corresponde (lector: `GET /api/mi/panel`; personal: `GET /api/dashboard`).
- Lector con sanción activa → **403** con la fecha de fin (el documento dice que el acceso queda restringido).
- Los ids de bibliotecario/lector/gerente **salen del token**, nunca del body.

## Endpoints por pantalla

Leyenda: 🌐 público · 👤 cualquier usuario logueado · 📚 lector · 🧑‍💼 bibliotecario o gerente · 👑 gerente

### Login / Inicio
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| POST | `/api/auth/login` | 🌐 | Iniciar sesión |
| POST | `/api/usuarios/registro` | 🌐 | Auto-registro de lector |
| GET | `/api/dashboard` | 🧑‍💼 | KPIs (libros, préstamos activos/vencidos, reservas en espera, usuarios, multas) + destacados |
| GET | `/api/mi/panel` | 👤 | Inicio reducido del lector: sus préstamos, reservas, deuda y avisos sin leer |
| GET | `/api/configuracion` | 🌐 | Nombre de la biblioteca, horario, etc. |

### Libros (RF01, RF02)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/libros` · `/api/libros/buscar?q=` · `/api/libros/{id}` · `/api/libros/destacados` | 🌐 |
| POST | `/api/libros` | 🧑‍💼 |
| PUT | `/api/libros/{id}` | 🧑‍💼 |

`estado` del libro (derivado): `DISPONIBLE`, `PRESTADO` o `SIN_EJEMPLARES`.

### Libros virtuales, portadas y PDF
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| GET | `/api/libros/isbn/{isbn}` | 🌐 | Escáner de código de barras: pregunta **a la vez** a Google Books y a Open Library, junta los datos (título, autores, editorial, año, páginas, géneros, portada), prueba también el ISBN-10/13 equivalente y los cruza con la biblioteca: `autores_existentes`/`autores_nuevos`, `generos_existentes`/`generos_nuevos` e `id_libro_existente` si ese ISBN ya está registrado |
| GET | `/api/archivos/portadas/{isbn}/{archivo}` | 🌐 | La portada servida por el backend (solo si no hay URL pública de R2) |
| POST | `/api/libros/completo` (multipart: `libro` + `portada` + `pdf`) | 🧑‍💼 | Alta de la obra original: tipo, precios, portada y PDF en un solo envío (las copias se dan de alta aparte, en `/api/ejemplares`). `libro` acepta `autores_nuevos` y `generos_nuevos` (nombres): se crean o se reutilizan por nombre (sin importar tildes ni mayúsculas). Si la portada de internet no se pudo bajar, el libro **sí** se guarda y la respuesta trae `aviso` |
| PUT | `/api/libros/{id}/completo` (multipart) | 🧑‍💼 | Edición completa (los ejemplares se editan aparte) |
| POST | `/api/libros/{id}/portada` · `/api/libros/{id}/pdf` | 🧑‍💼 | Sube o reemplaza la portada / el PDF |
| GET | `/api/almacenamiento/estado` | 🧑‍💼 | Si R2 está configurado y qué buckets encuentra |
| POST | `/api/almacenamiento/probar-escritura` | 🧑‍💼 | Prueba de escritura en R2 (deja un objeto de 0 bytes) |
| POST | `/api/almacenamiento/preparar` | 🧑‍💼 | Crea en R2 los buckets que falten (portadas y PDF) |

Tipos de libro: `FISICO` (copias físicas, dadas de alta aparte en `Ejemplares`), `VIRTUAL` (solo PDF) y `HIBRIDO` (las dos).
El virtual y el híbrido necesitan **precio de alquiler por día** y **precio de compra**
(el de compra tiene que ser mayor). El físico solo tiene precio de venta.

Portadas: bucket público de R2, solo se guarda el link (`url_imagen`).
PDF: bucket privado, solo se guarda la clave (`clave_pdf`); el lector recibe una URL firmada que caduca en 15 minutos.

### Libros virtuales (pedidos y pagos)
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| POST | `/api/accesos-virtuales` | 📚 | El lector pide el libro: `{id_libro, tipo: ALQUILER\|COMPRA, dias_alquiler, metodo_pago: YAPE\|EFECTIVO}` |
| GET | `/api/accesos-virtuales/mis-libros` | 👤 | Los libros virtuales del lector (perfil) |
| GET | `/api/accesos-virtuales/{id}/pdf?descargar=false` | 👤 | `{url}` firmado (15 min) para **leer** el PDF (`inline`). Con `descargar=true` el navegador lo guarda (`attachment`): solo si es una **compra** (o lo pide el personal) |
| POST | `/api/accesos-virtuales/{id}/comprobante` (multipart: `comprobante` + `referencia_yape`) | 👤 | El lector sube la captura de su pago con Yape |
| GET | `/api/accesos-virtuales/{id}/comprobantes` | 👤 | Los comprobantes que subió el lector de ese pedido |
| POST | `/api/accesos-virtuales/{id}/cancelar` | 👤 | Cancela un pedido que aún no se pagó |
| GET | `/api/accesos-virtuales/pedidos?estado=PENDIENTE_PAGO` | 🧑‍💼 | Pedidos por cobrar |
| GET | `/api/accesos-virtuales/codigo/{codigo}` | 🧑‍💼 | Busca la orden con el código del cliente (vence a las 24 h) |
| GET | `/api/accesos-virtuales/libro/{idLibro}` | 🧑‍💼 | Quién compró o alquiló un libro |
| POST | `/api/accesos-virtuales/{id}/cambiar` | 🧑‍💼 | Cambia la orden: de alquiler a compra o los días |
| POST | `/api/accesos-virtuales/{id}/cobrar` | 🧑‍💼 | Cobra en el mostrador y activa el acceso |
| GET | `/api/pagos/yape/qr` | 🌐 | El QR estático de Yape Negocio (imagen; va en un `<img>` sin token) |

El precio se **copia** en el pedido (igual que la tarifa de multa en el préstamo).
El estado del pedido sí se guarda (`PENDIENTE_PAGO`, `PAGADO`, `CANCELADO`): es el ciclo de vida
del pago. Lo que se calcula es si el alquiler ya venció, que sale de `fecha_fin`.

**Yape:** no hay API pública de Yape con QR dinámico y monto exacto. Lo implementado es el QR
**estático** de Yape Negocio (`src/main/resources/qr.jpeg`) con el monto escrito debajo: el
lector paga en su app tecleando ese monto, saca una captura y la sube como **comprobante**; el
personal la **aprueba o la deniega**. Un QR con el monto exacto exige un adquirente (Niubiz,
Nuvei, PayU, Kushki, EBANX, YabandPay) que cobra 3.5-5 % + S/0.30.

El comprobante **no cambia el estado del pedido**: sigue en `PENDIENTE_PAGO` hasta que alguien
lo acepta. "En revisión" es un estado **derivado** del último comprobante.

### Comprobantes de Yape (el personal los revisa)
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| GET | `/api/comprobantes/pendientes` | 🧑‍💼 | Los que esperan revisión |
| GET | `/api/comprobantes?estado=PENDIENTE\|APROBADO\|DENEGADO` | 🧑‍💼 | Listado por estado |
| GET | `/api/comprobantes/{id}/imagen` | 🧑‍💼 | `{url}` firmado (15 min) de la captura |
| POST | `/api/comprobantes/{id}/aprobar` | 🧑‍💼 | Aprueba: el pedido se paga, el lector tiene el PDF y recibe el aviso |
| POST | `/api/comprobantes/{id}/denegar` | 🧑‍💼 | Deniega con `{"motivo":"..."}`: el lector lo ve y puede subir otro |

Aprobar es idempotente: si el pedido ya estaba pagado, no se cobra dos veces ni se vuelve a
notificar.

### Caja (el cajón de dinero)
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| GET | `/api/cajas/actual` | 🧑‍💼 | La caja abierta + el resumen de hoy |
| POST | `/api/cajas` `{"monto_inicial":50,"observaciones":""}` | 🧑‍💼 | Abre el cajón (falla si ya hay una abierta) |
| POST | `/api/cajas/{id}/cerrar` `{"monto_final_declarado":120,"observaciones":""}` | 🧑‍💼 | Cierra y hace el arqueo (sobrante/faltante) |
| GET | `/api/cajas?desde=&hasta=` | 🧑‍💼 | Historial de cajas |
| GET | `/api/cajas/{id}` | 🧑‍💼 | Una caja con sus movimientos |
| GET | `/api/cajas/pendientes` | 🧑‍💼 | Préstamos sin devolver, reservas por recoger y en cola, pedidos sin cobrar |

Es **una sola caja** para toda la biblioteca (el personal se turna): la columna `caja_abierta` es
UNIQUE y la base no deja abrir dos a la vez. El dinero solo entra por `caja_movimientos`: si no
hay caja abierta, el cobro se rechaza con 400.

Movimientos: `COBRO_PRESTAMO_FECHA_FIJA`, `COBRO_PRESTAMO_ABIERTO` (al devolver, por el precio de
la copia), `COBRO_MULTA` (el efectivo recibido; el 15 % de descuento va en `descuento`),
`VENTA_VIRTUAL` y `ALQUILER_VIRTUAL`.

### Auditoría (quién hizo qué) — solo el gerente
| Método | Ruta | Quién | Para qué |
|---|---|---|---|
| GET | `/api/auditoria?desde=&hasta=&id_usuario=&accion=&pagina=0&tamanio=20` | 👑 | Historial de acciones, paginado |

Se auditan los libros, los ejemplares, los préstamos, las devoluciones, el cobro de multas, la
caja, los turnos, los comprobantes de Yape, los pedidos virtuales, los usuarios, las sanciones,
los avisos y la configuración. La auditoría se escribe en la **misma transacción** que la
operación: si la operación se revierte, la auditoría tampoco se guarda.

### Autores (RF03) y géneros
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/autores` · `/api/autores/{id}` · `/api/autores/{id}/libros` ("Ver libros") | 🌐 |
| POST / PUT / DELETE | `/api/autores` · `/api/autores/{id}` | 🧑‍💼 (no se elimina si tiene libros) |
| GET / POST | `/api/generos` | 🌐 / 🧑‍💼 |

### Ejemplares (RF14)
| Método | Ruta | Quién |
|---|---|---|
| POST | `/api/libros/{idLibro}/ejemplares` | 🧑‍💼 |
| GET | `/api/ejemplares?id_libro=&estado=&q=` · `/api/ejemplares/{id}` | 🧑‍💼 |
| PUT | `/api/ejemplares/{id}` `{"ubicacion":"B2","estado":"DANADO"}` | 🧑‍💼 |

Estados: `DISPONIBLE, PRESTADO, RESERVADO, DANADO, PERDIDO, BAJA`. A mano solo DISPONIBLE/DANADO/PERDIDO/BAJA.

**Precio por copia.** Cada ejemplar tiene su propio precio (`ejemplares.precio`), que puede ser
distinto del precio general del libro (una edición antigua, un tiraje raro). Si al dar de alta
la copia no se manda precio, se usa el `precio_compra` del libro. Se envía al registrar la copia
(`{"codigo_inventario":"INV-001","ubicacion":"A1","precio":35.00}`) y al actualizarla
(`{"precio":35.00}`; `null` significa "el precio no cambia"). La respuesta trae `precio` (el de
esta copia) y `precio_libro` (el general del libro).

### Usuarios (RF04)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/usuarios?q=&rol=LECTOR&pagina=0&tamanio=10` (paginado) | 🧑‍💼 |
| POST | `/api/usuarios` ("+ Registrar usuario", crea lector) | 🧑‍💼 |
| GET / PUT | `/api/usuarios/{id}` | 🧑‍💼 (bibliotecario edita lectores; gerente a todos) |
| GET / PUT | `/api/usuarios/me` | 👤 |

Respuesta paginada: `{"contenido":[...],"pagina":0,"tamanio":10,"total_elementos":57,"total_paginas":6}`.
`estado` del usuario (calculado): `ACTIVO`, `INACTIVO` o `SUSPENDIDO`.

### Préstamos (RF06) – asistente Usuario → Libro → Confirmar
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/usuarios?q=` (paso 1) · `/api/libros/buscar?q=` (paso 2) | 🧑‍💼 |
| POST | `/api/prestamos` `{"id_libro":1,"id_usuario_lector":3,"tipo_prestamo":"FECHA_FIJA"}` | 🧑‍💼 |
| GET | `/api/prestamos?estado=ACTIVOS\|VENCIDOS\|DEVUELTOS\|TODOS&q=` | 🧑‍💼 |

Con `id_libro` el sistema elige el ejemplar (el apartado por reserva o el primero disponible).
`tipo_prestamo`: `FECHA_FIJA` (vence en `dias_max`) o `ABIERTO` (sin fecha, **nunca multa**).

El precio del ejemplar se **copia** al préstamo (`prestamos.precio_copia`), igual que la tarifa
de multa: es el precio de esa copia en el momento del préstamo, así que cambiar el precio del
libro o de la copia después no altera los préstamos ya hechos. Vale igual en las dos rutas
(fecha fija y abierto). Si la copia no tiene precio propio, se copia el precio del libro.

### Devoluciones (RF07, RF08)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/prestamos?estado=ACTIVOS` (tabla con `en_plazo` y `dias_retraso`) | 🧑‍💼 |
| POST | `/api/devoluciones` `{"id_prestamo":1,"observaciones":"...","estado_ejemplar":"DISPONIBLE"}` | 🧑‍💼 |

### Reservas (RF09)
| Método | Ruta | Quién |
|---|---|---|
| POST | `/api/reservas` `{"id_libro":1}` | 📚 |
| POST | `/api/reservas/mostrador` `{"id_libro":1,"id_usuario_lector":5}` | 🧑‍💼 (anota a un lector en la cola desde el mostrador) |
| GET | `/api/mi/reservas` | 👤 |
| PATCH | `/api/reservas/{id}/cancelar` | 👤 (el lector solo las suyas) |
| GET | `/api/reservas?estado=PENDIENTE` · `/api/reservas/cola/{idLibro}` | 🧑‍💼 |

### Pagos de multa (RF15)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/multas?estado=PENDIENTES\|PAGADAS\|TODAS` | 🧑‍💼 |
| POST | `/api/multas/{id}/pagar` `{"monto_pagado":1.00,"metodo_pago":"EFECTIVO"}` | 🧑‍💼 |
| GET | `/api/multas/pagos?desde=&hasta=` (auditoría) | 🧑‍💼 |
| GET | `/api/mi/multas` · `/api/mi/prestamos` | 👤 |

Métodos: `EFECTIVO, TARJETA, TRANSFERENCIA, YAPE, PLIN, OTRO`. Acepta **pago parcial** (estado `PARCIAL`).

### Sanciones y bloqueos (RF17)
| Método | Ruta | Quién |
|---|---|---|
| POST | `/api/usuarios/{id}/suspender` `{"motivo":"...","fecha_fin":"2026-10-15"}` | 🧑‍💼 |
| GET | `/api/sanciones?activas=true` · `/api/usuarios/{id}/sanciones` | 🧑‍💼 |
| PATCH | `/api/sanciones/{id}/levantar` | 🧑‍💼 |

### Avisos y notificaciones (RF11)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/notificaciones?no_leidas=true` · `/api/notificaciones/no-leidas/total` | 👤 |
| PATCH | `/api/notificaciones/{id}/leida` · `/api/notificaciones/leidas` | 👤 |
| POST | `/api/notificaciones` ("+ Crear aviso") `{"id_usuario":5,"mensaje":"..."}` (sin id = a todos los lectores) | 🧑‍💼 |
| GET | `/api/notificaciones/todas?tipo=&pagina=0&tamanio=20` | 🧑‍💼 |
| POST | `/api/notificaciones/generar` | 👑 |

Tipos: `VENCIMIENTO_PROXIMO, PRESTAMO_VENCIDO, RESERVA_DISPONIBLE, RESERVA_VENCIDA, MULTA_PENDIENTE, NUEVO_LIBRO, ACCESO_VIRTUAL, COMPROBANTE_YAPE, PAGO_RECHAZADO, ADMINISTRATIVO`.
Los interruptores de "Configuración de avisos" están en `PUT /api/configuracion` (`aviso_vencimiento`, `aviso_reserva`, `aviso_multa`, `aviso_nuevo_libro`).

### Bitácora de turnos (RF16)
| Método | Ruta | Quién |
|---|---|---|
| POST | `/api/turnos/abrir` · `/api/turnos/cerrar` | 🧑‍💼 |
| GET | `/api/turnos/actual` (204 si no hay) | 🧑‍💼 |
| GET | `/api/turnos?desde=&hasta=&id_bibliotecario=&tipo=GERENTE` | 🧑‍💼 (el bibliotecario solo ve los suyos) |

### Configuración (RF13)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/configuracion` | 🌐 |
| PUT | `/api/configuracion` (solo los campos enviados) | 👑 |
| GET | `/api/configuracion/historial` | 👑 |

```json
{"nombre_biblioteca":"Biblioteca Orduña","correo_contacto":"contacto@biblioteca.pe","telefono_contacto":"064...",
 "hora_apertura":"08:00","hora_cierre":"20:00",
 "tarifa_multa_diaria":1.00,"dias_max":7,"dias_retiro_reserva":2,
 "aviso_vencimiento":true,"aviso_reserva":true,"aviso_multa":true,"aviso_nuevo_libro":true}
```

### Reportes (RF10) y cierre diario (RF12)
| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/reportes/inventario` · `/prestamos?desde=&hasta=` · `/multas?desde=&hasta=` | 🧑‍💼 |
| GET | `/api/reportes/{inventario\|prestamos\|multas}/csv` (descarga) | 🧑‍💼 |
| POST | `/api/cierre-diario` · GET `/api/cierre-diario?desde=&hasta=` · `/api/cierre-diario/{fecha}` | 🧑‍💼 |

## Reglas de negocio del documento (implementadas)

| Regla | Dónde |
|---|---|
| Préstamo con fecha fija o abierto; el abierto nunca genera multa | `PrestamoService`, `DevolucionService` |
| Multa = días de retraso × tarifa; la tarifa se guarda en el préstamo, así un cambio posterior no es retroactivo | `Prestamo.tarifaMultaDiaria`, `configuracion_multas` versionada |
| El precio del ejemplar se copia al préstamo, igual en fecha fija y abierto | `Prestamo.precioCopia`, `Ejemplar.precio` |
| Solo se presta si hay ejemplar disponible; si no, se reserva | `PrestamoService`, `ReservaService` |
| Solo se reserva un libro sin ejemplares disponibles | `ReservaService.crearReserva` |
| Reservas FIFO: al liberarse un ejemplar se aparta para el primero y se le avisa | `ReservaService.asignarEjemplarLiberado` |
| La reserva vence si no se recoge a tiempo (`dias_retiro_reserva`) y pasa al siguiente | tarea diaria 00:10 |
| Avisos automáticos de vencimiento próximo / vencido | tarea diaria 8:00 |
| Cierre diario con préstamos activos y vencidos | `CierreDiarioService` + tarea 23:55 |
| Contraseñas cifradas (BCrypt) | `SecurityConfig.passwordEncoder` |
| Acceso por rol (lector, bibliotecario, gerente) | `SecurityConfig` |
| Sancionado: no inicia sesión, no presta, no reserva; estado SUSPENDIDO; finaliza sola o al levantarla | `SancionService`, `AuthController` |
| Lector con multa pendiente o parcial no puede pedir préstamos | `PrestamoService` |
| No se elimina un autor con libros asociados | `AutorService.eliminarAutor` |
| El estado de los ejemplares lo cambian préstamos, devoluciones y reservas | `EjemplarService` |
| El alta de libro es solo de la obra original: las copias se dan de alta aparte, en Ejemplares (un libro puede no tener ninguna todavía) | `LibroService.crearEjemplares`, `EjemplarService.registrarEjemplar` |
| Al devolver un préstamo (fecha fija **o** abierto) se cobra el precio de la copia; la multa se cobra aparte | `DevolucionService`, `CajaService` |
| Pagar en efectivo aplica 15 % de descuento: el monto digitado es la deuda que se salda y la biblioteca cobra el 85 % | `DevolucionService.DESCUENTO_EFECTIVO` |
| La caja es una sola: solo una abierta, con monto inicial y arqueo al cerrar | `CajaService`, columna `cajas.caja_abierta` UNIQUE |
| No se cobra sin caja abierta | `CajaService.registrar` → `OperacionInvalidaException` (400) |
| El pago con Yape es QR estático + comprobante; el personal lo aprueba o lo deniega | `ComprobanteService`, `PagoYapeController` |
| El comprobante no cambia el estado del pedido ("en revisión" es derivado) | `AccesoVirtualResponseDTO.estadoComprobante` |
| Las acciones del personal quedan auditadas; solo el gerente las ve | `AuditoriaService`, `AuditoriaController` |

## Errores (GlobalExceptionHandler)

```json
{"timestamp":"2026-10-01T18:30:00","status":400,"error":"Bad Request",
 "mensaje":"Hay datos inválidos en la petición","path":"/api/libros",
 "errores":{"ids_autores":"Debe indicar al menos un autor"}}
```

| HTTP | Cuándo |
|---|---|
| 400 | Validación `@Valid`, regla de negocio (`OperacionInvalida`, `EjemplarNoDisponible`, `ReservaActivaDuplicada`, `TurnoPendiente`), JSON o parámetro mal formado |
| 401 | Sin token / token vencido / credenciales incorrectas |
| 403 | Rol sin permiso, usuario sancionado, editar/cancelar algo ajeno (`AccesoDenegado`) |
| 404 | Id inexistente |
| 409 | ISBN, DNI, email, código de inventario o género repetido; cierre del día ya hecho |

## Diferencias con el documento (decisiones)

- `libros_generos` usa una tabla `generos` con id (`id_libro`, `id_genero`) en vez de un texto libre: sigue en 4FN y evita géneros mal escritos.
- El estado ACTIVO/INACTIVO/SUSPENDIDO del usuario se **calcula** (cuenta activa + sanción vigente) en vez de guardarse, para que nunca quede desincronizado.
- "¿Olvidaste tu contraseña?" y "Recordar sesión" son del frontend; el backend no envía correos.
- Al pagar una multa no se levanta una sanción automáticamente: la deuda ya bloquea préstamos por sí sola, y la sanción se levanta con `PATCH /api/sanciones/{id}/levantar`.
