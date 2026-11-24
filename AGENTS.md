# AGENTS.md — Reglas de trabajo para este repositorio

Este archivo es el contrato para cualquier agente de IA (y para cualquier
persona) que vaya a leer o modificar **Sistema de Biblioteca Orduña**.
Define qué hace el sistema, cómo se organiza, qué reglas de negocio son
intocables, qué convenciones hay que respetar y qué está prohibido hacer.

Si algo aquí contradice tu criterio propio, **gana este archivo**: el código
existente manda sobre el estilo que traigas de otro proyecto.

El detalle de cómo funciona el sistema está en [`readme.txt`](./readme.txt).
Las decisiones documentadas del proyecto están en
`biblioteca-orduna/LEEME.md` y `biblioteca-orduna/frontend/README.md`.

---

## 1. Mapa del proyecto

```
biblioteca-orduna/
├── src/main/java/com/biblioteca/     Backend Spring Boot (Java 17)
│   ├── entity/        Clases JPA → tablas MySQL
│   ├── repository/    JpaRepository
│   ├── service/       LÓGICA DE NEGOCIO (la capa importante)
│   ├── controller/    Endpoints REST (HTTP puro, sin lógica)
│   ├── dto/           Records Java de entrada/salida
│   ├── security/      JWT, filtro, matriz de permisos
│   ├── realtime/      SSE: canal /api/eventos, eventos "cambio" y "notificacion"
│   ├── exception/     Excepciones tipadas de negocio
│   ├── web/           Manejador global de errores
│   ├── tarea/         @Scheduled (4 tareas; una cierra la caja por horario)
│   └── config/        @EnableScheduling
├── src/main/resources/ application.properties + data.sql
└── frontend/          SPA React 18 + Vite
    └── src/
        ├── api/       Un módulo por recurso, una función por endpoint
        ├── context/   AuthContext, RealtimeContext (SSE), ToastContext
        ├── routes/    AppRoutes, ProtectedRoute
        ├── components/ common/ (reutilizables, incl. EscanerCodigo y QrYape) + layout/ (armazón)
        ├── pages/     29 archivos .jsx en 21 carpetas (25 rutas; añadida ConfiguracionCaja)
        ├── utils/     sesion.js, formato.js, estados.js
        └── styles/    global.css
```

---

## 2. Reglas de oro (no negociables)

1. **La lógica de negocio vive en `service/`.** Un controlador no valida reglas
   de negocio, no calcula multas y no toca repositorios directamente. Si tu
   código tiene un `if` de negocio dentro de un controlador, está mal ubicado.

2. **El actor sale del token, nunca del cuerpo de la petición.** El
   bibliotecario, lector o gerente se obtiene con `@AuthenticationPrincipal
   UsuarioAutenticado`. **Prohibido** aceptar un `id_bibliotecario`,
   `id_usuario` o `id_lector` del body para saber quién está actuando. Esto es
   un fallo de seguridad crítico, no un estilo.

3. **Nunca devolver la contraseña hash ni el JWT a quien no corresponde.**
   El DTO de usuario no lleva `contrasena_hash`. El token solo se emite en
   `POST /api/auth/login`.

4. **El estado del libro, del usuario y del préstamo se CALCULA, no se
   guarda.** Si añades una columna `estado` a `libros` o `usuarios`, rompes el
   diseño y el dato se desincroniza del historial. `LibroService` y
   `UsuarioService.estadoDe()` son la única fuente de verdad.

5. **La tarifa de multa se COPIA en el préstamo.** En la creación del préstamo
   se lee la configuración vigente y se guarda `tarifaMultaDiaria` en la fila
   del préstamo. Al calcular la multa se lee SIEMPRE la del préstamo, nunca la
   configuración actual. Es lo que hace que cambiar la tarifa no altere multas
   ya calculadas. No "simplifiques" esto.

6. **La cola de reservas es FIFO estricto.** Orden: `fechaReserva` y luego
   `id`. Al liberarse un ejemplar se asigna al primero y **solo a uno**.

7. **El ISBN se normaliza antes de comparar**: se le quitan guiones y espacios
   y se pasa a mayúsculas. Si añades un camino que compare el ISBN en crudo,
   rompes el dedupe.

8. **Toda excepción de negocio usa una clase de `exception/`**, nunca
   `RuntimeException` genérica, para que el `GlobalExceptionHandler` la
   traduzca al código HTTP correcto.

9. **El JSON va en `snake_case`** (`id_usuario`, `fecha_prestamo`,
   `codigo_inventario`). Los DTO son `record`s de Java y sus componentes se
   escriben en `camelCase`; el mapeo a `snake_case` es responsabilidad de
   Jackson (configurado con `SNAKE_CASE`). Respeta el nombre que ya usan los
   DTO existentes.

10. **No inventes dependencias.** El proyecto es deliberadamente pequeño:
    Spring Boot, JPA, Security, jjwt en el backend; React, Router y Axios en el
    frontend. Antes de añadir una librería, pregúntate si cabe en unas pocas
    líneas a mano.

11. **No uses Lombok.** El proyecto no lo usa y los DTO son `record`s. Escribe
    los constructores y getters a mano.

12. **No introduzcas `Optional.get()` sin verificar**, ni captures
    `Exception` a lo ancho, ni te tragues errores para "que no se caiga la
    pantalla". Si algo falla, propaga la excepción tipada.
13. **El libro declara su precio y su tipo, y sus archivos van a Cloudflare R2.**     Al registrar un libro, el bibliotecario o el gerente elige el tipo
    (`FISICO`, `VIRTUAL`, `HIBRIDO`) y el precio. La **portada** va al bucket
    **público** de R2 y el **PDF** al bucket **privado**. En la base de datos
    **solo se guarda el link** (`url_imagen`) o **la clave** (`clave_pdf`):
    nunca el binario, nunca la URL firmada.
    - Las claves de R2 van en un **`.env`** (ver `biblioteca-orduna/LEEME.md`),
      nunca en `application.properties` (ese archivo se sube al repositorio).
    - **Sin URL pública de R2 las portadas se ven igual**: las sirve el backend en
      `GET /api/archivos/portadas/{isbn}/{archivo}` (público, caché de 1 año) y en la
      base queda el link relativo. Con `R2_URL_PUBLICO_PORTADAS` las entrega Cloudflare.
      El link se rearma al leerlo (`AlmacenamientoService.urlVisible`): cambiar la URL
      pública no obliga a tocar la base. El frontend resuelve los links con
      `urlArchivo()` (`api/axiosClient.js`).
    - Las claves llevan versión (`portadas/{isbn}/{marca}.jpg`, `pdfs/{isbn}/{marca}.pdf`)
      y el archivo viejo se borra **al confirmar** la transacción (si se revierte, se
      borra el nuevo). No vuelvas a claves fijas: el navegador cachearía la portada vieja.
    - El PDF **no** se sirve con una URL fija: se entrega con una URL firmada
      que caduca en 15 minutos, solo a quien lo compró/alquiló o al personal.
14. **Escáner de ISBN.** Al escanear el código de barras, el formulario se
    rellena solo (título, autores, editorial, año, páginas, **géneros** y portada).
    - Google Books y Open Library se consultan **a la vez** y se juntan: Google manda
      en los datos, Open Library rellena huecos y aporta la portada grande. Si con el
      ISBN-13 no hay nada, se prueba el ISBN-10 equivalente (y al revés).
    - La respuesta viene cruzada con la biblioteca: `autores_existentes` /
      `autores_nuevos`, `generos_existentes` / `generos_nuevos` (comparando sin
      tildes ni mayúsculas) e `id_libro_existente` si el ISBN ya está registrado.
      Los nuevos se crean **al guardar el libro** (`autores_nuevos`,
      `generos_nuevos` en `RegistroLibroCompletoDTO`), en la misma transacción.
    - El ISBN se valida con su **dígito de control** (frontend y backend): un número
      con un dígito de más, de menos o cambiado se rechaza con un mensaje claro en
      vez de decir "no se encontró". `GOOGLE_BOOKS_API_KEY` (opcional) evita el 429
      de Google en el VPS; la clave va en cabecera, nunca en la URL. Si Google y
      `/api/books` no lo conocen, se prueba el buscador `search.json` de Open Library.
    - La portada es **opcional**: si no se puede bajar de internet, el libro se
      guarda igual y la respuesta trae `aviso` (el frontend lo muestra como toast).
      Un archivo que el bibliotecario elige a mano sí es obligatorio que suba bien.
    - **No se usa ninguna librería de códigos de barras en el backend**: el
      navegador usa el lector físico (que actúa como teclado) y el ISBN
      viaja como texto a `GET /api/libros/isbn/{isbn}`. Ahí se busca el libro
      en **Google Books** (`https://www.googleapis.com/books/v1/volumes?q=isbn:`)
      y, si no está, en **Open Library** (`https://openlibrary.org/api/books`).
      Son APIs públicas y gratuitas: **no hacen falta claves**.
    - La portada que devuelve la búsqueda se **descarga y se guarda en R2**
      (solo se aceptan `books.google.com`, `books.googleusercontent.com`,
      `covers.openlibrary.org` y `covers.archive.org`).
    - **Físico**: el formulario registra **solo la obra original**: su precio
      general y su portada. **Las copias NO se crean ahí**: se dan de alta en
      el módulo **Ejemplares** ("+ Registrar ejemplar") o desde la ficha del
      libro ("Agregar o editar copias"), que abre `Ejemplares` con el libro ya
      elegido. Cada ejemplar lleva su **código de inventario**,
      **ubicación**, **estado** y su **precio propio** (si no se indica, toma
      el precio general del libro). Los ejemplares se **agrupan bajo el
      libro** (un libro con 5 ejemplares es un libro con 5 filas agrupadas):
      solo los ejemplares se reservan.
    - **Virtual**: se sube el **PDF** y hay **dos precios**: alquiler por día
      (solo lectura dentro de la app) y compra (más caro, permite descargar
      el PDF). El libro comprado aparece en el perfil del usuario y el
      personal puede ver quién lo compró.
    - **Pago**: solo hay **dos** formas, y **Mercado Pago ya no se usa**
      (no hay checkout de tarjeta):
      - **Yape por QR + comprobante**: el lector ve el QR **estático** de
        Yape Negocio (`src/main/resources/qr.jpeg`, servido por
        `GET /api/pagos/yape/qr`) con el **monto escrito debajo** (el QR
        no lo lleva). Paga en su app tecleando ese monto, saca una
        **captura** y la sube como **comprobante** (`comprobantes_pago`, en
        el bucket privado de R2). El personal la **aprueba** (el pedido se
        paga y el lector recibe el "felicidades, compra exitosa, disfruta
        de tu libro pagado", o "felicidades, disfruta tus N días
        restantes") o la **deniega** (el lector ve el motivo y puede
        subir otro). **El comprobante NO cambia el estado del pedido**:
        sigue en `PENDIENTE_PAGO`; "en revisión" es un estado **derivado**.
      - **Efectivo en tienda**: el cliente recibe un **número de cola** y
        un **código de 24 horas** (atado a su cuenta). El personal tiene
        un panel extra, **"Reservas de libro virtual"**, donde mete el
        código del cliente y decide: **cancelar orden**, **cambiar orden**
        o **cobrar orden**.
      Cuando el período de alquiler acaba, el libro sale **en plomo** en
      su perfil y no se puede abrir hasta que vuelva a reservarlo.
    - **Yape**: no existe API pública de Yape con QR dinámico y monto exacto
      (solo un adquirente: Niubiz, Nuvei, PayU, Kushki, EBANX, YabandPay, con
      3.5-5 % + S/0.30). Si el proyecto exige el QR con el monto, hay que
      contratar al adquirente: no es un detalle de código. Lo implementado
      es el QR estático + el comprobante que aprueba el personal.
15. **React Native (APK) para el modo móvil.** Se hace **al final**, cuando
    el sistema web esté cerrado. El personal escanea con el celular y el
    inventario se actualiza aunque la sesión esté abierta en la PC de
    recepción (por eso el estado del ejemplar se bloquea con
    `@Lock(PESSIMISTIC_WRITE)`). El sitio tiene que **aguantar 1000 clientes**,
    muchos libros y **carga mínima**: el VPS ya está comprado y se accede a
    él **por IP** (sin dominio).
    - Ojo: el **escaneo ya funciona en el navegador del celular** (la cámara
      con `EscanerCodigo.jsx`) y en la PC (el lector físico). El APK es para
      el modo del personal en el mostrador, no para poder escanear: eso ya
      está resuelto sin dependencias nuevas.

---

## 3. Convenciones del backend

- **DTOs = `record`.** Convenciones de nombre: `Registro*` (entrada con
  validaciones), `Actualizar*` (update parcial), `Nuevo*`, `Response*` /
  `*ResponseDTO` (salida). No uses clases POJO con getters/setters para DTOs.
- **Validación con `@Valid` + Bean Validation** en el controlador
  (`@NotBlank`, `@NotNull`, `@Size`, `@Min`...). Los fallos los formatea
  `GlobalExceptionHandler` al campo `errores`.
- **Transacciones en el servicio**, no en el controlador. `@Transactional` para
  escritura, `@Transactional(readOnly = true)` para lectura.
- **Excepciones disponibles** (úsalas en vez de crear nuevas si encajan):

  | Excepción                      | HTTP |
  |--------------------------------|------|
  | `OperacionInvalidaException`   | 400  |
  | `EjemplarNoDisponibleException`| 400  |
  | `ReservaActivaDuplicadaException`| 400 |
  | `TurnoPendienteException`      | 400  |
  | `CredencialesInvalidasException`| 401 |
  | `UsuarioSancionadoException`   | 403  |
  | `AccesoDenegadoException`      | 403  |
  | `RecursoNoEncontradoException` | 404  |
  | `RecursoDuplicadoException`    | 409  |

- **Relaciones unidireccionales.** El modelo no usa `@OneToMany` ni
  `@ElementCollection`; `Libro` es dueño de sus colecciones. Para el lado
  inverso se usa una consulta de repositorio. No introduzcas
  relaciones bidireccionales "para ahorrar una consulta": crean serialización
  recursiva y bucles JSON.

- **Consultas parametrizadas** siempre (`:param` o `@Param`). No concatenes
  cadenas en JPQL/SQL.

---

## 4. Convenciones del frontend

- **Pasa por `api/axiosClient.js`.** No llames a `axios` directamente en una
  página: ahí vive el `Authorization` y el manejo global de 401.
- **Un módulo de API por recurso** en `src/api/`. Una función por endpoint,
  nombres en `verboEntidad` (`crearLibro`, `listarPrestamos`).
- **Estado local con los hooks de `src/hooks.js`**, no con `useEffect` suelto:
  - `useApi(funcion, deps, inicial)` para lecturas → `{ datos, setDatos, cargando, recargar }`.
  - `useAccion()` para escrituras → `{ ejecutar, enviando }`, muestra el toast solo.
  - `useDebounce(valor, ms)` para búsquedas mientras se escribe.
- **No introduzcas Redux/Zustand.** El estado global son `AuthContext` y
  `ToastContext`. Mantenlo así.
- **Rutas:** regístralas en `AppRoutes.jsx` y pon el nivel de acceso con
  `ProtectedRoute`. Si es una pantalla nueva de gestión, añade también su
  enlace en `Sidebar.jsx` dentro del bloque y rol correctos.
- **Estilos:** CSS propio. Reutiliza `components/common/` (`Modal`, `Campo`,
  `Badge`, `Estado`, `Paginacion`, `StatCard`...). Usa las variables de
  `styles/global.css`; no introduzcas colores sueltos ni otra librería de UI.
- **Estados y badges:** si añades un estado nuevo, actualiza a la vez
  `utils/estados.js` y el componente `Estado.jsx` para que tenga color y
  etiqueta en español.
- **Modo invitado (solo visual):** `entrarComoInvitado()` de `AuthContext` crea
  una sesión **en memoria**, sin token y sin `localStorage`. `ProtectedRoute`
  solo le deja ver `/inicio` y `AppLayout` intercepta clics, envíos y Enter con
  el aviso "Un bibliotecario te debe crear tu cuenta". El rol `INVITADO` no
  existe en la base de datos: **no lo persistas ni lo mandes al backend**.
- **Carga global:** `components/common/CargadorLibro.jsx` (libro pasando
  páginas, puntos de carga y frase motivadora) se muestra al abrir la app y en
  cada cambio de ruta; se monta una sola vez en `AppRoutes.jsx`.
- **Escaneo de códigos:** usa `components/common/EscanerCodigo.jsx`. En la PC
  el lector de códigos actúa como teclado (escribe el número y se pulsa
  Enter); en el celular, la cámara. La cámara usa la API **nativa** del
  navegador (`window.BarcodeDetector` + `getUserMedia`): **no añadas una
  librería de códigos de barras**. Dos límites que no puedes saltarte:
  hace falta contexto seguro (https o localhost, no `http://IP`) y Firefox no
  lo soporta. Cuando no se puede usar la cámara, el campo sigue aceptando el
  código escrito a mano.

---

## 5. Reglas de negocio intocables

Estas reglas dan sentido al sistema. Si cambias una, cambia también
`readme.txt`, `LEEME.md` y la documentación académica, y explica por qué.

1. Un préstamo es **fijo** (`FECHA_FIJA`: vence en los `dias_max` de la
   configuración) o **personalizado** (`PERSONALIZADO`: el personal elige
   cuántos días, de 1 a 365, y se guarda en `prestamos.dias_prestamo`).
   `ABIERTO` **ya no se puede crear** (400); el valor sigue en el enum solo para
   leer préstamos antiguos. No lo borres o esas filas dejan de cargar.
2. Los dos tipos tienen fecha de devolución y **los dos generan multa** por
   retraso. Solo un préstamo `ABIERTO` antiguo no genera multa.
3. Multa = días de retraso × tarifa; la tarifa es la **copiada en el préstamo**.
   Al prestar, el personal usa la tarifa de la configuración o escribe una
   **personalizada** (`tarifa_multa_diaria` en el body; queda
   `prestamos.tarifa_personalizada = true`). En los dos casos se copia en el
   préstamo y cambiar la configuración después no la altera.
   Si el libro vuelve `DANADO`, `PERDIDO` o `BAJA` se suma un **cargo por
   daño/pérdida** que decide el bibliotecario en el formulario de devolución
   (el frontend sugiere 50% del precio de la copia si está dañado y 100% si
   está perdido, y permite editarlo; 0 = no cobrar). La devolución genera
   **una sola** multa con el retraso + el cargo, y guarda el motivo en
   `multas.concepto` ("Retraso (3 días)", "Libro dañado",
   "Retraso (2 días) + libro perdido"). El cargo **no** se cobra al devolver:
   queda como deuda y se paga en "Pagos de multas". Una devolución que quedó
   sin multa puede recibir una **multa manual** desde "Pagos de multas"
   (`POST /api/multas`): solo crea la deuda, nunca cobra en el acto.
4. Solo se presta si hay un ejemplar `DISPONIBLE`; si no, se reserva.
5. Solo se reserva un libro que **no** tenga ejemplares disponibles.
6. Las reservas son FIFO (fecha de reserva, luego id); al liberar un ejemplar
   se avisa al primero y **solo a uno**.
7. Una reserva notificada vence si no se recoge en `dias_retiro_reserva` y pasa
   al siguiente de la cola.
8. El lector **sancionado** no puede iniciar sesión, ni prestar, ni reservar.
9. El lector **con multa pendiente o parcial** no puede pedir préstamos nuevos.
10. Un lector no puede tener dos reservas activas del mismo libro.
11. No se elimina un autor con libros asociados (400).
12. No se elimina un ejemplar con historial de préstamos (400).
13. El `codigo_inventario` es único e inmutable.
14. Las contraseñas se guardan con BCrypt, nunca en claro.
15. El acceso se restringe por rol (ver `SecurityConfig`).
16. El estado del libro y del usuario se derivan, no se persisten.
17. El cierre diario registra los préstamos activos y vencidos del día.
18. **Pagar en efectivo da 15% de descuento** (`DevolucionService.DESCUENTO_EFECTIVO`).
    El monto que el cajero digita es la deuda que quiere saldar: en efectivo la
    biblioteca solo cobra el 85%, y la diferencia queda en `pagos_multa.descuento`.
    La deuda se reduce por el monto completo, no solo por el efectivo recibido.
    Los demás métodos de pago cobran el monto completo.
19. Un libro es `FISICO`, `VIRTUAL` o `HIBRIDO`. El virtual y el híbrido
    necesitan **PDF**. El físico **no** necesita ejemplares para poder
    registrarse: el libro es la obra original y sus **copias** se dan de alta
    aparte, en el módulo **Ejemplares**. Un libro recién registrado puede no
    tener ninguna copia todavía: sin ejemplares no se presta (se reserva),
    pero el libro sí existe. Un libro **virtual** no admite ejemplares: sus
    copias son el PDF, y **no se reserva** (se alquila o se compra).
20. El precio de un **pedido de libro virtual se copia en el pedido**: cambiar
    el precio del libro no altera los pedidos ya hechos.
21. El **código de retiro** de una orden pagada en tienda vale **24 horas** y
    está atado a esa cuenta. Al cobrar la orden, el código se anula.
22. El PDF se entrega con una **URL firmada que caduca en 15 minutos**, solo a
    quien lo compró o alquiló (o al personal). Nunca se devuelve la clave del
    objeto ni una URL fija.
23. Un alquiler de libro virtual **vence por `fecha_fin`**: después sale en
    plomo y no se puede abrir hasta volver a reservarlo.
24. Las imágenes y los PDF viven en **Cloudflare R2**, no en la base de datos:
    en la base queda el link (portada) o la clave (PDF).
25. El estado de un **pedido** de libro virtual sí se persiste
    (`PENDIENTE_PAGO`, `PAGADO`, `CANCELADO`): es el ciclo de vida del pago,
    no un estado derivado. Lo que se deriva es si el alquiler venció.
26. El precio de la **copia** (`ejemplares.precio`) puede ser distinto del
    precio del libro, y **se copia al préstamo** (`prestamos.precio_copia`),
    igual que la tarifa de multa: cambiar el precio después no altera los
    préstamos ya hechos. Vale igual en las dos rutas, `FECHA_FIJA` y
    `PERSONALIZADO`. Si la copia no tiene precio propio, se usa el del libro.
27. La **caja es una sola** para toda la biblioteca: solo puede haber una
    abierta a la vez (cada apertura es una caja numerada: C001, C002...). La
    columna `cajas.caja_abierta` es UNIQUE (1 mientras está abierta, NULL al
    cerrarla): la base impide abrir dos a la vez. **Solo el GERENTE abre y
    cierra la caja** (403 para el bibliotecario). Al abrirla registra el
    **monto inicial** y elige un **ENCARGADO** (`cajas.id_encargado`, un
    bibliotecario o gerente activo). El gerente puede **cambiar el encargado**
    sin cerrar la caja (`PUT /api/cajas/{id}/encargado`); cada tramo queda en
    `caja_encargados` (quién, desde, hasta, asignado por). Al final el
    **encargado cuenta el efectivo** (`POST /api/cajas/{id}/arqueo`,
    `cajas.monto_contado_encargado`) y el gerente cierra: si no escribe otro
    monto, se usa el del encargado. La caja **no** lleva `turno_id`: caja
    (sesión de dinero) y turno (bitácora del personal) conviven.
28. **Solo el encargado de la caja abierta o el gerente cobran**
    (`CajaService.puedeCobrar`, comprobado dentro de `registrar()`). Los demás
    bibliotecarios prestan y devuelven, pero no reciben dinero.
    **El precio de la copia se cobra AL PRESTAR** (`prestamos.cobro_al_prestar = true`):
    si presta el encargado o el gerente, se cobra en el acto (`metodo_pago` en el
    body) y el préstamo nace `ACTIVO`; si presta otro bibliotecario, nace
    **`POR_COBRAR`**: el ejemplar queda apartado (`PRESTADO`) pero el libro no sale
    hasta que el encargado lo cobra (`POST /api/prestamos/{id}/cobrar`; las fechas
    del préstamo se cuentan desde el cobro). Si el cliente no paga, se **anula**
    (`POST /api/prestamos/{id}/anular`, estado `ANULADO`) y el ejemplar se libera
    a la cola de reservas. Un `POR_COBRAR` no se puede devolver.
    Los **préstamos antiguos** (`cobro_al_prestar = false`, los que salieron antes
    de este cambio) siguen pagando el precio **al devolver**, y ese cobro también
    lo hace el encargado. La multa se cobra **aparte**, con su 15% de descuento en
    efectivo. Si la copia no tiene precio, no hay cobro.
29. **No se presta, devuelve ni cobra sin caja abierta**: el préstamo guarda la
    caja donde empezó (`prestamos.id_caja_inicio`, la caja donde se pagó) y la
    devolución la caja donde terminó (`prestamos.id_caja_fin`); la multa queda en
    la caja donde se pagó (su movimiento). Así las cajas se **relacionan**: un
    préstamo de la caja C001 se puede devolver en la C003 y pagar la multa ahí.
    `CajaService.registrar()` es el único punto por donde entra dinero. El
    movimiento de caja es la **única fuente de verdad** del dinero: el total del
    día es la suma de los movimientos del día.
30. El pago con **Yape es QR estático + comprobante**: el personal **aprueba
    o deniega** la captura. El pedido **no cambia de estado** al subir el
    comprobante ("en revisión" es un estado derivado). Aprobar es
    idempotente: un pedido ya pagado no se cobra ni se notifica dos veces.
31. Las acciones del personal quedan **auditadas** (quién hizo qué) y solo el
    gerente las ve. Incluye asignar encargado, arqueo, cobrar y anular préstamos
    y las notas del CRM. La auditoría se escribe en la **misma transacción** que
    la operación: si la operación se revierte, la auditoría tampoco se
    guarda. Por eso un método que audita no puede ser `readOnly = true`.
32. El horario de la caja lo configura el **gerente**, un día por fila
    (`horarios_caja.dia_semana` es UNIQUE), con encargado, hora de inicio y
    hora de fin. El bibliotecario ve su horario en la pantalla Caja. La caja
    se **cierra sola** al pasarse la hora de fin de hoy, solo si se abrió hoy
    y antes de esa hora; si hoy no hay horario, no se cierra automáticamente.
    El cierre automático asume el efectivo esperado (no hay arqueo humano) y
    eso queda escrito en las observaciones y en la auditoría.

---

## 6. Al añadir un endpoint o una pantalla

Backend:
1. DTO `record` en `dto/` con el prefijo que corresponda.
2. Consulta en `repository/` si hace falta (parametrizada).
3. Regla de negocio + `@Transactional` en `service/`.
4. Método en `controller/` que solo delegue y use `@Valid`.
5. Permiso en `SecurityConfig` (**en el orden correcto**: público → solo
   gerente → solo lector → autenticado → admin → por defecto).
6. Si puede fallar, lanza la excepción tipada correcta.
7. Swagger: anota con `@Operation` y `@ApiResponse` como los demás. **OJO:**
   el proyecto **no tiene** springdoc: no hay ni una anotación de Swagger en
   el código. No añadas la dependencia sin avisar; si la pides, va en su propio
   cambio.
8. Si toca R2 (portada o PDF): el archivo va en `multipart` (`@RequestPart`),
   la subida la hace `AlmacenamientoService` y a la base solo llega el link o
   la clave. Los secretos van en el `.env`, nunca en el código.

Frontend:
9. Función en `src/api/` del recurso correspondiente.
10. Si es pantalla nueva: carpeta en `pages/Nombre/` con `Nombre.jsx` +
    `Nombre.css`, registrada en `AppRoutes` con su acceso, y enlace en
    `Sidebar`.
11. Usa `useApi` / `useAccion`, `Modal`, `Campo`, `Badge` y toasts.

---

## 7. Qué NO hacer (prohibiciones)

- ❌ No aceptar el id del actor desde el body (regla 2).
- ❌ No meter lógica de negocio en el controlador.
- ❌ No devolver entidad JPA directamente al JSON: siempre un `*ResponseDTO`
  (evita exponer campos internos y bucles de serialización).
- ❌ No cambiar el orden de `SecurityConfig` sin entender el `firstMatch`: es
  posicional y un orden distinto abre o cierra rutas sin querer.
- ❌ No desactives la validación de rol "para probar" y lo dejes así.
- ❌ No guardes contraseñas en claro ni registres tokens o contraseñas en logs.
- ❌ No añadas dependencias pesadas ni cambies el stack sin motivo.
- ❌ No uses `ddl-auto=create` en `application.properties` de forma permanente
  (destruye datos). El valor correcto es `update`, y si cambias el modelo en
  MySQL borras la base a mano, porque `update` no altera columnas existentes.
- ❌ No refactorices en grande ni renombres archivos "porque sí" dentro de un
  cambio de funcionalidad. Cambios grandes = un commit aparte.
- ❌ No borres `data.sql` ni los usuarios de prueba; son el seed del proyecto.
- ❌ No subas secretos nuevos a `application.properties` (ya hay clave de BD y
  de JWT; es deuda técnica existente, no un patrón a copiar).
- ❌ No cobres fuera de la caja: todo dinero que entra pasa por
  `CajaService.registrar()`, que exige una caja abierta y que cobre su
  encargado o el gerente. No saltes esa comprobación "para probar".
- ❌ No muestres las notas internas del CRM (`notas_cliente`) al lector: son solo
  del personal (`/api/crm/**` es solo del gerente).
- ❌ No cambies el estado de un pedido al subir un comprobante de Yape ("en
  revisión" es derivado) ni cobres dos veces el mismo pedido.
- ❌ No devuelvas la clave del objeto de R2 ni una URL fija: el PDF y la
  captura del comprobante se entregan con URL firmada de 15 minutos.

---

## 8. Cómo levantar el proyecto para verificar

```bash
# Backend (terminal 1) — http://localhost:8080
cd biblioteca-orduna
mvn spring-boot:run

# Frontend (terminal 2) — http://localhost:5173
cd biblioteca-orduna/frontend
npm install
npm run dev
```

- Base de datos: `CREATE DATABASE biblioteca_orduna;`
- Usuario/clave de MySQL: `root` / `denis123` (según
  `src/main/resources/application.properties`). Ojo: `LEEME.md` y
  `COMO-EJECUTAR.md` dicen `sebas123`, están desactualizados; el valor real
  es `denis123`.
- El frontend llama al backend en `http://localhost:8080/api`
  (`frontend/.env`, variable `VITE_API_URL`). No hay proxy de Vite; el backend
  habilita CORS para el 5173.
- Usuarios de prueba: `admin@biblioteca.pe/admin123` (gerente),
  `biblio@biblioteca.pe/biblio123` (bibliotecario),
  `lector@biblioteca.pe/lector123` (lector).

Verificación rápida de que el arranque es correcto:
- `GET http://localhost:8080/api/salud` → `{"estado":"funcionando", ...}` (antes era `GET /`; en producción `/` es la página del frontend)
- Login desde `http://localhost:5173/login` con cualquiera de los tres usuarios.

---

## 9. Estado real del proyecto (léelo antes de prometer algo)

Sé honesto con lo que el proyecto **no** tiene, porque un agente que no lo sabe
puede asumir que existe:

- **No hay ninguna prueba automática.** `src/test/java/com/biblioteca` está
  vacía y el `pom.xml` trae dependencias de test sin usar. No escribas
  "lo probé con tests" si no los has añadido.
- No hay CI, lint ni formateador.
- No hay carga perezosa de pantallas ni límites de error en React.
- No hay refresh de token ni revocación: un JWT vale hasta 8 horas.
- No hay correo, ni recuperación de contraseña (el enlace "¿Olvidaste tu
  contraseña?" es decorativo).
- No hay API para crear bibliotecarios o gerentes; sus cuentas vienen de
  `data.sql`.
- Solo tres endpoints paginan (`/api/usuarios`, `/api/notificaciones/todas` y
  `/api/auditoria`); el resto devuelve el array completo. `useApi` no expone
  un campo de error: un fallo se ve igual que una lista vacía.
- **Concurrencia: ya está corregida, no la rompas.** La elección de ejemplar del
  préstamo, la cabeza de la cola FIFO de reservas y el cobro de una multa usan
  `@Lock(PESSIMISTIC_WRITE)` (`findByIdForUpdate`,
  `findFirstByLibroIdAndEstadoOrderByIdAsc`,
  `findFirstByLibroIdAndEstadoOrderByFechaReservaAscIdAsc`). Verificado con 8
  préstamos simultáneos del mismo ejemplar: 1 aceptado y 7 rechazados. Si
  añades una consulta que lea y luego escriba el estado de un ejemplar o de una
  multa, hazlo igual de bloqueado, o el defecto vuelve.
  **Orden de bloqueo: primero la CAJA, después el préstamo, el ejemplar o la multa.**
  `CajaService.cajaAbiertaParaOperar()` y `exigirPuedeCobrar()` devuelven la caja
  bloqueada y se llaman al principio de prestar, cobrar, devolver y pagar una multa.
  Si la caja se toma al final, dos operaciones a la vez chocan (MySQL pone un bloqueo
  compartido en la caja al guardar un préstamo con `id_caja_inicio` y el cobro pide
  después uno exclusivo): con 40 préstamos simultáneos fallaban 32. La devolución
  bloquea el préstamo (`findByIdForUpdate`), así que devolver dos veces a la vez da 400.
  **Reservas**: se bloquea al lector (`UsuarioService.bloquear`) y la transacción es
  `READ_COMMITTED`, para que el segundo de dos clics vea la reserva del primero. No
  uses una lectura bloqueada de `reservas`: bloquea huecos del índice y frena a otros
  lectores que reservan el mismo libro. Si aun así MySQL corta una transacción por
  bloqueo, `GlobalExceptionHandler` responde **409** "Otra persona estaba registrando
  lo mismo..." en vez de 500. Prueba de carga (100 usuarios, 40 préstamos, 60 reservas
  y cobros dobles a la vez): sin errores 500 ni datos duplicados.
- `useAccion` ya trata un 204 sin cuerpo como éxito (antes `res.data` era `""` y
  algunas pantallas mostraban "error" aunque el guardado había funcionado).
- **Rendimiento del catálogo**: `LibroService.aDTOs()` cuenta copias por libro y libros
  por autor en **dos consultas en bloque** (`EjemplarRepository.contarPorLibros`,
  `LibroRepository.contarPorAutores`) y `hibernate.default_batch_fetch_size=100`
  carga autores y géneros por lotes. No vuelvas a contar dentro de un bucle por libro.
- **Hay que reiniciar el backend** para que carguen las rutas nuevas: el proceso
  de Java se queda con el código viejo. El escáner de ISBN avisa de dos cosas
  distintas: si el 404 **no trae mensaje** (o trae "La ruta no existe") es que
  el backend va viejo y hay que reiniciarlo (Ctrl+C y `mvn spring-boot:run`);
  si el 404 **trae mensaje**, es cosa del negocio: ni Google Books ni Open
  Library reconocen ese ISBN, y el libro se registra escribiendo los datos a
  mano. Las columnas nuevas (`ejemplares.precio`, `prestamos.precio_copia`)
  las agrega solas Hibernate al arrancar con `ddl-auto=update`; no hace falta
  borrar la base (eso solo hace falta si cambias el tipo o el nombre de una
  columna).
- **La cámara del escáner necesita https o localhost.** En el VPS, que se
  entra por IP (`http://`), la cámara no se puede abrir todavía: ahí se usa el
  lector físico o se escribe el código a mano. Firefox tampoco soporta
  `BarcodeDetector`.
- **Libros virtuales (reglas 13 y 14): lo que falta por fuera del código.**
  - El bucket **privado** `biblioteca-pdfs` **todavía no existe** en la cuenta
    de R2: sin él, el alta de un libro virtual falla al subir el PDF **y
    también falla subir un comprobante de Yape** (las capturas van al mismo
    bucket, en `comprobantes/`). Se crea desde la propia aplicación: el botón
    **"Preparar almacenamiento"** de **Configuración → Almacenamiento**
    (`POST /api/almacenamiento/preparar`, solo gerente) lo crea con las mismas
    credenciales que usa la aplicación, si el token de R2 tiene permiso para
    administrar buckets. Si no lo tiene, hay que crearlo a mano en
    Cloudflare → R2 → Create bucket (sin acceso público).
  - Falta `R2_URL_PUBLICO_PORTADAS` en el `.env` (R2 → bucket `biblioteca` →
    Settings → Public Development URL → `https://pub-<hash>.r2.dev`). Sin ella
    las portadas **sí se ven** (las sirve el backend en
    `/api/archivos/portadas/...`), pero para el VPS conviene ponerla. Esta URL
    **no se puede deducir**: hay que copiarla del panel de Cloudflare al `.env`.
  - El estado se ve en **Configuración → Almacenamiento** (solo gerente), con
    los botones "Preparar almacenamiento" y "Probar escritura".
  - **Ya no hacen falta credenciales de Mercado Pago**: ese checkout se
    eliminó. El pago en línea es Yape por QR estático + comprobante, y el
    pago en efectivo en la tienda sigue como estaba.
  - **Despliegue**: `Dockerfile` en la raíz (un solo contenedor con backend +
    frontend compilado) y MySQL como recurso aparte en Coolify. Guía paso a paso
    en [`DESPLIEGUE-COOLIFY.md`](./DESPLIEGUE-COOLIFY.md). En producción la
    configuración llega por variables de entorno (`DB_URL`, `DB_USERNAME`,
    `DB_PASSWORD`, `JWT_SECRET`, `SHOW_SQL`, `R2_*`, `FRONTEND_URL`,
    `CORS_ALLOWED_ORIGINS`, `GOOGLE_BOOKS_API_KEY`). `FrontendConfig` sirve el
    `index.html` para las rutas de React; todo GET fuera de `/api/` es público.
  - **Yape con QR dinámico y monto exacto no existe como API pública**: hay
    que contratar a un adquirente (3.5-5 % + S/0.30). Lo implementado es el
    QR estático de Yape Negocio (el monto lo teclea el cliente) y el
    comprobante que aprueba el personal.
- **`prestamos.tipo_prestamo` es VARCHAR(20), no ENUM.** Hibernate 6 la creó como
  ENUM de MySQL y `ddl-auto=update` no cambia columnas existentes: `data.sql`
  termina con un `ALTER TABLE prestamos MODIFY COLUMN tipo_prestamo VARCHAR(20)`
  (idempotente) para que entre `PERSONALIZADO`. Si agregas valores a otro enum
  que ya exista en la base (`@Enumerated(STRING)`), haz lo mismo o el INSERT falla.
- **"Nuevo préstamo" muestra en gris los libros sin copias libres** con la fecha en
  que vuelve la primera copia prestada: `LibroResponseDTO.proxima_devolucion`
  (`PrestamoRepository.proximaDevolucionPorLibros`, una consulta en bloque).
- **Perfil del lector**: sección "Reserva de libro físico" (día y hora de inicio,
  día de devolución, multa por día y multa acumulada) y "Detalle de multas"
  (`pages/Prestamos/ReservasFisicas.jsx`, usa `/api/mi/prestamos` y `/api/mi/multas`).
- **Caja (reglas 27-29): sin caja abierta no se presta, no se devuelve ni se
  cobra.** Al probar en local, entra como **gerente** (`admin@biblioteca.pe`),
  abre la caja en la sección "Caja" (monto inicial `0.00` sirve) y elige al
  encargado. Si eliges al bibliotecario, sus préstamos se cobran en el acto; los
  de cualquier otro quedan "por cobrar". El seed solo trae un bibliotecario: para
  probar "otro bibliotecario" crea una cuenta de prueba a mano en tu base local.
- **Más columnas que eran ENUM de MySQL**: `prestamos.estado` (VARCHAR(20), para
  `POR_COBRAR` y `ANULADO`) y `auditorias.accion` (VARCHAR(40)) se convierten al
  final de `data.sql`, igual que `tipo_prestamo`. `data.sql` también pone
  `cobro_al_prestar = 0` a los préstamos antiguos y `id_encargado = id_usuario_apertura`
  a las cajas antiguas. Las demás columnas `@Enumerated` siguen siendo ENUM nativos:
  si les agregas un valor, agrega su ALTER.
- **CRM: Ficha 360 del cliente** (solo gerente): `GET /api/crm/clientes/{id}` y las
  notas internas (`/api/crm/clientes/{id}/notas`, `DELETE /api/crm/notas/{id}`),
  pantalla `pages/Clientes/Clientes.jsx` (`/clientes`, también desde Usuarios).
  Todo se lee de las tablas que ya existen (`CrmService`); lo único nuevo que se
  guarda son las notas (`notas_cliente`).
- **Cierre automático (regla 32): por defecto no hay ningún horario**, así que
  la caja no se cierra sola hasta que el gerente guarde horarios en
  "Configuración de caja". La tarea corre cada minuto en hora de Lima. Para
  probarlo sin esperar, guarda un horario para hoy con hora de fin un par de
  minutos posterior a la hora actual. El cierre usa el encargado del horario
  (o el de la caja) como responsable del arqueo automático.
- **Auditoría (regla 31): la tabla `auditorias` no se purga sola** y crece con
  cada acción del personal. Si hay que limpiarla, se borran a mano las filas
  antiguas.
- **No hay Swagger/OpenAPI en el código** (no hay dependencia de springdoc),
  aunque el punto 7 de la sección 6 lo pida. Ningún controlador del proyecto
  usa `@Operation`/`@ApiResponse`.
- **App móvil**: la web es **instalable** (PWA: `frontend/public/manifest.json`,
  iconos y `sw.js`, registrado en `main.jsx` solo en producción). Desde ella se
  genera el **APK** con PWABuilder (Trusted Web Activity); ver
  [`APK-ANDROID.md`](./APK-ANDROID.md). El service worker **nunca** cachea `/api/`:
  no lo cambies, o el celular mostraría datos viejos o de otra sesión. La app
  nativa en React Native (regla 15) sigue pendiente para el final.

---

## 10. Definición de "terminado" para un cambio

Antes de dar un cambio por bueno:

- [ ] Arranca el backend (`mvn spring-boot:run`) sin errores.
- [ ] Arranca el frontend (`npm run dev`) sin errores de consola.
- [ ] La regla de negocio nueva está en `service/`, transaccional.
- [ ] Si añade endpoint: DTO, repository (si aplica), service, controller,
      permiso en `SecurityConfig`, función en `api/` del frontend, y ruta
      + `Sidebar` si es pantalla nueva.
- [ ] Si puede fallar, lanza la excepción tipada correcta (no 500 genérico).
- [ ] Respeté el control de acceso por rol de la sección 5/7.
- [ ] Si la acción es del personal y tiene que quedar auditada, la llamé
      desde `service/` con `auditoriaService.registrar(...)` (en la misma
      transacción que la operación).
- [ ] Si el cambio mueve dinero, todo pasó por `CajaService.registrar()`.
- [ ] No introduced dependencias nuevas ni cambios de stack.
- [ ] Si el cambio altera una regla de negocio o una tabla, actualicé
      `readme.txt` y `LEEME.md`.
- [ ] Probé de verdad el flujo afectado (no solo que compile).

---

## 11. Referencias internas

| Archivo                             | Qué contiene                                        |
|-------------------------------------|-----------------------------------------------------|
| `readme.txt`                        | Cómo funciona el sistema, de principio a fin.        |
| `biblioteca-orduna/LEEME.md`        | Detalle de endpoints y reglas del backend.           |
| `biblioteca-orduna/COMO-EJECUTAR.md`| Guía de arranque (ojo: clave de BD desactualizada). |
| `biblioteca-orduna/frontend/README.md`| Detalle del frontend.                             |
| `application.properties`            | Puerto, BD, JWT (8 h), CORS, `show-sql`.            |

Cuando algo de este documento deje de ser cierto, **corrige el documento en el
mismo commit** que el cambio de código. La documentación desactualizada es un
defecto, no un detalle.