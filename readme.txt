================================================================================
 SISTEMA DE BIBLIOTECA ORDUÑA
 Documentacion tecnica de como funciona el sistema
================================================================================

Este archivo explica, de principio a fin, como esta construido y como funciona el
sistema de gestion de biblioteca. Esta escrito para que una persona o un agente
de IA pueda entender el proyecto sin necesidad de leer todo el codigo.

Para las reglas de trabajo del repositorio, ver AGENTS.md.


--------------------------------------------------------------------------------
1. QUE ES EL SISTEMA
--------------------------------------------------------------------------------

Es una aplicacion web de gestion de biblioteca para la institucion Orduña.
Reemplaza el control manual (fichas en papel) por un sistema digital que cubre:

  - Catalogo de libros, autores y generos.
  - Control de ejemplares (copias fisicas) con codigo de inventario.
  - Prestamos y devoluciones con calculo automatico de multas.
  - Reservas con cola de espera FIFO.
  - Sanciones y bloqueos a usuarios.
  - Pagos de multas (totales y parciales).
  - Avisos y notificaciones automaticas.
  - Bitacora de turnos del personal.
  - Reportes exportables a CSV y cierre diario.
  - Configuracion parametrica versioning.
  - Libros virtuales: venta y alquiler de PDFs (los archivos viven en R2).
  - Pago con Yape por QR estatico, con el comprobante que aprueba el personal.
  - Caja: el cajon de dinero (cuanto entro, por que metodo, que falta por recoger).
  - Auditoria: que hizo cada persona del personal, con fecha y hora.

Existen tres roles de usuario:

  LECTOR        El que pide prestado, reserva y consulta el catalogo.
  BIBLIOTECARIO El personal que atiende el mostrador.
  GERENTE       El que administra la configuracion y ve todos los reportes.


--------------------------------------------------------------------------------
2. TECNOLOGIAS
--------------------------------------------------------------------------------

BACKEND
  Java 17
  Spring Boot 3.3.4
  Spring Web          (controladores REST)
  Spring Data JPA     (Hibernate / acceso a datos)
  Spring Security     (autenticacion y permisos)
  jjwt 0.12.6         (firma y validacion de tokens JWT, algoritmo HS256)
  AWS SDK v2          (S3Client + S3Presigner contra Cloudflare R2)
  MySQL 8             (base de datos)
  BCrypt              (cifrado de contrasenas)

  No se usa Lombok. Los DTO son "records" de Java.
  No hay pruebas automaticas (ver seccion 16).

FRONTEND
  React 18
  Vite 5              (servidor de desarrollo y compilacion)
  React Router 6      (navegacion)
  Axios               (cliente HTTP)
  CSS escrito a mano  (sin Tailwind, sin Bootstrap, sin libreria de UI)

  No hay Redux, Zustand ni ninguna libreria de estado global.
  El estado vive en dos Context de React (AuthContext y ToastContext).
  No hay linter, ni formateador, ni pruebas.


--------------------------------------------------------------------------------
3. ESTRUCTURA DE CARPETAS
--------------------------------------------------------------------------------

biblioteca-orduna-completo/
|
|-- readme.txt                Este archivo.
|-- AGENTS.md                 Reglas para agentes y personas que trabajen aqui.
|-- README.md                 Documentacion breve de arranque.
|-- .gitignore
|
`-- biblioteca-orduna/
    |
    |-- pom.xml
    |-- LEEME.md               Detalle de endpoints y reglas (backend).
    |-- COMO-EJECUTAR.md       Guia de ejecucion.
    |
    |-- src/main/java/com/biblioteca/
    |   |
    |   |-- BibliotecaApplication.java   Punto de entrada. Activa @Scheduling.
    |   |
    |   |-- entity/       (39 archivos)  Clases JPA mapeadas a tablas MySQL.
    |   |-- repository/   (21 archivos)  Interfaces JpaRepository.
    |   |-- service/      (23 archivos)  REGLAS DE NEGOCIO. El cerebro del sistema.
    |   |-- controller/   (25 archivos)  Endpoints HTTP. Solo traduccion HTTP.
    |   |-- dto/          (67 archivos)  Records de entrada y salida JSON.
    |   |-- security/     (5 archivos)   JWT, filtro, matriz de permisos.
    |   |-- exception/    (11 archivos)  Excepciones de negocio.
    |   |-- web/          (2 archivos)   Manejador global de errores.
    |   |-- tarea/        (1 archivo)    Tareas programadas (@Scheduled).
    |   `-- config/       (4 archivos)   @EnableScheduling, R2, web.
    |
    |-- src/main/resources/
    |   |-- application.properties   Configuracion (BD, JWT, CORS).
    |   |-- data.sql                 Datos iniciales (usuarios, config, generos).
    |   |-- qr.jpeg                  QR estatico de Yape Negocio (el del mostrador).
    |   `-- {CD2B0866-...}.png       Archivo suelto, se puede borrar.
    |
    `-- frontend/
        |-- package.json, vite.config.js, index.html
        |-- .env                  VITE_API_URL=http://localhost:8080/api
        |-- .env.example          Misma variable, para copiar
        `-- src/
            |-- main.jsx, App.jsx, hooks.js
            |-- api/              (20 modulos) Una funcion por endpoint.
            |-- context/          AuthContext, ToastContext
            |-- routes/           AppRoutes, ProtectedRoute
            |-- components/
            |   |-- common/       Badge, Estado, Modal, Campo, Paginacion,
            |   |                 Portada, StatCard, SearchInput, Cargando,
            |   |                 EscanerCodigo, QrYape
            |   `-- layout/       AppLayout, Sidebar, Topbar
            |-- pages/            (19 carpetas, 26 pantallas: 23 enrutadas
            |                     + FormLibro, PedidoVirtual y FormSancion)
            |-- utils/            sesion.js, formato.js, estados.js
            `-- styles/           global.css (tokens de diseno)


--------------------------------------------------------------------------------
4. COMO SE LEVANTA
--------------------------------------------------------------------------------

REQUISITOS
  Java 17 o superior
  Maven 3.9 o superior
  MySQL 8 o superior
  Node.js 18 o superior

PASO 1 - Base de datos (una sola vez)

  CREATE DATABASE biblioteca_orduna;

  IMPORTANTE: si ya existia una version anterior de esta base y cambiaste el
  MODELO (no solo anadas una columna), BORRALA primero
  (DROP DATABASE biblioteca_orduna;). El sistema usa ddl-auto=update: crea
  tablas nuevas y ADIENCE columnas nuevas a las que ya existen (por ejemplo
  ejemplares.precio o prestamos.precio_copia, que se agregan solos al
  reiniciar), pero NO cambia ni borra columnas que ya existen. Si cambiaste el
  tipo o el nombre de una columna, tienes que borrar la base a mano.

  Usuario y contrasena de MySQL: en
  biblioteca-orduna/src/main/resources/application.properties
      spring.datasource.username=root
      spring.datasource.password=denis123

  (Nota: LEEME.md y COMO-EJECUTAR.md dicen "sebas123". El valor real y el que
   funciona es denis123. Ver seccion 17.)

PASO 2 - Backend (terminal 1)

  cd biblioteca-orduna
  mvn spring-boot:run

  Arranca en http://localhost:8080
  Al iniciar, Hibernate crea las tablas y despues data.sql inserta los datos
  iniciales (3 usuarios, configuracion general, tarifas, 6 generos).

  Comprobar: GET http://localhost:8080/api/salud  ->  {"estado":"funcionando", ...}

PASO 3 - Frontend (terminal 2)

  cd biblioteca-orduna/frontend
  npm install        (solo la primera vez)
  npm run dev

  Abre http://localhost:5173

  El frontend llama al backend por URL absoluta (http://localhost:8080/api).
  No hay proxy de Vite, por eso el backend tiene CORS abierto para el puerto 5173.
  Si el puerto 5173 esta ocupado, Vite sube a 5174 y el navegador bloqueara las
  peticiones por CORS. Libera el 5173 o agrega el puerto nuevo a
  cors.allowed-origins.

USUARIOS DE PRUEBA

  Rol            Correo                 Contrasena
  -------------  ---------------------  -----------
  GERENTE        admin@biblioteca.pe    admin123
  BIBLIOTECARIO  biblio@biblioteca.pe   biblio123
  LECTOR         lector@biblioteca.pe   lector123


--------------------------------------------------------------------------------
5. ARQUITECTURA DEL BACKEND
--------------------------------------------------------------------------------

El backend tiene cuatro capas, siempre en este orden:

  Peticion HTTP
      |
      v
  controller/   Recibe JSON, valida con @Valid, delega. NO tiene reglas de negocio.
      |
      v
  service/      Aqui vive TODA la logica. Es transaccional (@Transactional).
      |
      v
  repository/   Interfaces JpaRepository. Solo consultas a la base de datos.
      |
      v
  entity/       Clases JPA mapeadas a tablas.

Regla de oro: si estas escribiendo una regla de negocio (validar, calcular,
notificar), va en service/, NO en controller/.

Ejemplo del flujo real de "registrar un prestamo":

  POST /api/prestamos
      -> PrestamoController              (desempaqueta el JSON)
      -> PrestamoService.registrarPrestamo()   (toda la logica)
           -> UsuarioService.buscarPersonal()     (el bibliotecario sale del token)
           -> UsuarioService.buscarLector()       (el lector sale del body)
           -> SancionService.verificarSinSancion() -> 403 si esta suspendido
           -> MultaRepository.exists...           -> 400 si tiene deudas
           -> ReservaRepository / EjemplarRepository -> elige el ejemplar
           -> ConfiguracionService.obtenerParametros() -> dias y tarifa
           -> PrestamoRepository.save()
      -> devuelve PrestamoResponseDTO como JSON


--------------------------------------------------------------------------------
6. LA BASE DE DATOS
--------------------------------------------------------------------------------

23 entidades JPA, que producen 25 tablas fisicas (contando las dos tablas puente).

  Tabla                 Contenido
  --------------------  --------------------------------------------------
  usuarios              Personas: lectores, bibliotecarios y gerentes.
                        contrasena_hash, rol, activo (booleano).
  autores               Nombre, nacionalidad, biografia.
  libros                Titulo, isbn (unico), editorial, anio, paginas.
                        tipo_libro, precio_alquiler_diario, precio_compra,
                        url_imagen (portada en R2), clave_pdf (PDF en R2).
  ejemplares            Copia fisica: codigo_inventario (unico), ubicacion, estado,
                        precio (precio propio de ESTA copia; si es null se usa
                        el precio_compra del libro).
  libros_autores        Tabla puente N:M entre libros y autores.
  libros_generos        Tabla puente N:M entre libros y generos.
  generos               Catalogo de generos (tabla propia, no texto libre).
  prestamos             Prestamo: ejemplar, lector, bibliotecario, tipo,
                        fecha_prestamo, fecha_devolucion_esperada,
                        tarifa_multa_diaria, precio_copia, estado.
  devoluciones          Cierre del prestamo (1:1 con prestamos): fecha, dias_retraso.
  multas                Deuda generada (1:1 con devoluciones): monto, monto_pagado, estado.
  pagos_multa           Cobros: monto, metodo_pago, fecha, bibliotecario que cobra.
  reservas              Cola FIFO: libro, lector, ejemplar_asignado, fechas, estado.
  sanciones              Motivo, fecha_inicio, fecha_fin, fecha_levantamiento.
  notificaciones        Avisos por usuario: tipo, mensaje, leida.
  bitacora_turnos       Apertura y cierre de turno del personal.
  cierres_diarios       Snapshot diario: totales de prestamos activos y vencidos.
  configuracion         Fila unica (id=1): nombre, horarios, interruptores de avisos.
  configuracion_multas  Versionado de tarifas: vigencia_desde / vigencia_hasta.
  accesos_virtuales     Pedido de libro virtual: lector, libro, tipo (alquiler/compra),
                        estado, precio (copiado), dias_alquiler, metodo_pago,
                        numero_cola, codigo_retiro, fechas de inicio y fin.
  comprobantes_pago     Captura del pago con Yape: pedido, lector, monto (copiado),
                        clave_imagen (en el bucket privado de R2), estado
                        (PENDIENTE/APROBADO/DENEGADO), revisado_por, motivo.
  cajas                 El cajon de dinero: fecha_apertura, fecha_cierre, quien
                        abrio y quien cerro, monto_inicial, monto_final_declarado,
                        diferencia (sobrante/faltante), estado y la columna
                        caja_abierta (UNIQUE: 1 mientras esta abierta, NULL al
                        cerrarla; es lo que impide abrir dos cajas a la vez).
  caja_movimientos      Cada sol que entra al cajon: caja, tipo, monto, descuento,
                        metodo_pago, descripcion, fecha, quien lo cobro y las
                        referencias al prestamo, al pago de multa, a la devolucion
                        o al pedido virtual. id_prestamo e id_pago_multa son
                        UNIQUE: no se cobra dos veces lo mismo.
  auditorias            Que hizo cada persona del personal: usuario (puede ser
                        null en acciones publicas), accion, entidad, entidad_id,
                        descripcion y fecha.

ENUMERACIONES (se guardan como texto, @Enumerated(EnumType.STRING))

  Rol               LECTOR, BIBLIOTECARIO, GERENTE
  TipoPrestamo      FECHA_FIJA, ABIERTO
  EstadoPrestamo    ACTIVO, DEVUELTO
  EstadoEjemplar    DISPONIBLE, PRESTADO, RESERVADO, DANADO, PERDIDO, BAJA
  EstadoReserva     PENDIENTE, NOTIFICADA, ATENDIDA, CANCELADA, VENCIDA
  EstadoMulta       PENDIENTE, PARCIAL, PAGADA
  EstadoUsuario     ACTIVO, INACTIVO, SUSPENDIDO   (calculado, no se guarda)
  EstadoTurno       ABIERTO, CERRADO
  TipoUsuario       ESTUDIANTE, DOCENTE, EXTERNO
  MetodoPago        EFECTIVO, TARJETA, TRANSFERENCIA, YAPE, PLIN, OTRO
  TipoNotificacion  VENCIMIENTO_PROXIMO, PRESTAMO_VENCIDO, RESERVA_DISPONIBLE,
                    RESERVA_VENCIDA, MULTA_PENDIENTE, NUEVO_LIBRO,
                    ACCESO_VIRTUAL, COMPROBANTE_YAPE, PAGO_RECHAZADO,
                    ADMINISTRATIVO
  TipoLibro         FISICO, VIRTUAL, HIBRIDO
  TipoAccesoVirtual ALQUILER, COMPRA
  EstadoAccesoVirtual PENDIENTE_PAGO, PAGADO, CANCELADO
  EstadoComprobante PENDIENTE, APROBADO, DENEGADO
  EstadoCaja        ABIERTA, CERRADA
  TipoMovimientoCaja COBRO_PRESTAMO_FECHA_FIJA, COBRO_PRESTAMO_ABIERTO,
                    COBRO_MULTA, VENTA_VIRTUAL, ALQUILER_VIRTUAL
  TipoAccionAuditoria REGISTRAR_LIBRO, EDITAR_LIBRO, ESCANEAR_ISBN, SUBIR_PORTADA,
                    SUBIR_PDF, REGISTRAR_EJEMPLAR, EDITAR_EJEMPLAR,
                    REGISTRAR_PRESTAMO, REGISTRAR_DEVOLUCION, COBRAR_MULTA,
                    ABRIR_CAJA, CERRAR_CAJA, ABRIR_TURNO, CERRAR_TURNO,
                    ACEPTAR_COMPROBANTE, DENEGAR_COMPROBANTE, COBRAR_PEDIDO_VIRTUAL,
                    REGISTRAR_USUARIO, EDITAR_USUARIO, APLICAR_SANCION,
                    LEVANTAR_SANCION, CREAR_AVISO, CAMBIAR_CONFIGURACION

ESTADOS QUE SE CALCULAN (no se guardan en la base)

  Estos tres se derivan en tiempo de ejecucion. Si anadirias una columna para
  ellos, rompes el diseno y se desincronizan.

  - Estado del LIBRO.    Se calcula contando sus ejemplares en LibroService:
                         sin ejemplares  -> SIN_EJEMPLARES
                         alguno disponible-> DISPONIBLE
                         ninguno          -> PRESTADO
                         Ojo: un libro con todos sus ejemplares DANADO, PERDIDO,
                         BAJA o RESERVADO se muestra como "PRESTADO".

  - Estado del USUARIO.  UsuarioService.estadoDe():
                         activo = false      -> INACTIVO
                         con sancion vigente -> SUSPENDIDO
                         en otro caso        -> ACTIVO

  - Estado del PRESTAMO en pantalla. Un prestamo ACTIVO con fecha vencida se
                         muestra como "VENCIDO", pero en la tabla sigue ACTIVO.

  OJO, diferencia importante: el estado de un PEDIDO DE LIBRO VIRTUAL si se
  guarda (accesos_virtuales.estado). No es un estado derivado: es el ciclo de
  vida de un pago (pendiente -> pagado -> cancelado), y el pedido tiene que
  saber en que punto esta para que el mostrador sepa que cobrar. Lo que si se
  calcula es si el ALQUILER ya vencio, que sale de fecha_fin.

Relaciones: todo el modelo es unidireccional. No hay ningún @OneToMany ni
@ElementCollection. Libro es dueño de las colecciones de autores y generos.
Para consultar el lado inverso se usan consultas de repositorio.


--------------------------------------------------------------------------------
7. SEGURIDAD Y SESIONES
--------------------------------------------------------------------------------

AUTENTICACION

  POST /api/auth/login  { "email": "...", "password": "..." }

  Si las credenciales son correctas devuelve:
    { "token": "eyJ...", "tipo": "Bearer", "expira_en_ms": 28800000,
      "id_usuario": 2, "nombre": "...", "email": "...", "rol": "BIBLIOTECARIO" }

  El token es un JWT firmado con HS256. Dura 8 horas. Lleva dentro:
    sub   = correo
    id   = identificador del usuario
    nombre = nombre completo
    rol  = LECTOR | BIBLIOTECARIO | GERENTE

  El frontend lo guarda bajo la clave "biblioteca_sesion" y lo envia en cada
  peticion como:  Authorization: Bearer <token>

  Regla critica: el id del bibliotecario, lector o gerente SIEMPRE sale del
  token, nunca del cuerpo de la peticion. Los controladores lo reciben con
  @AuthenticationPrincipal UsuarioAutenticado. Si alguna vez te pide un id de
  actor en el body, es un fallo de seguridad.

  Un lector con sancion activa recibe 403 al iniciar sesion.

MATRIZ DE PERMISOS (definida en SecurityConfig.java, se evalua en orden)

  PUBLICO
    POST /api/auth/login
    POST /api/usuarios/registro
    GET  /api/libros/**, /api/autores/**, /api/generos/**
    GET  /api/configuracion
    GET  /api/pagos/yape/qr        (es una imagen para un <img>: sin token)
    GET  /

  SOLO GERENTE
    PUT  /api/configuracion/**
    GET  /api/configuracion/historial
    GET  /api/auditoria/**         (quien hizo que)
    POST /api/notificaciones/generar
    POST /api/almacenamiento/preparar   (crea en R2 los buckets que falten)

  SOLO LECTOR
    POST /api/reservas
    POST /api/accesos-virtuales    (pedir un libro virtual)

  CUALQUIER USUARIO AUTENTICADO
    /api/mi/**          (panel, mis prestamos, multas, reservas, sanciones)
    /api/usuarios/me
    GET  /api/notificaciones, /api/notificaciones/no-leidas/total
    PATCH /api/notificaciones/*/leida, /api/notificaciones/leidas
    PATCH /api/reservas/*/cancelar
    GET  /api/accesos-virtuales/mis-libros, /api/accesos-virtuales/*/pdf,
         /api/accesos-virtuales/*/comprobantes
    POST /api/accesos-virtuales/*/comprobante, /api/accesos-virtuales/*/cancelar

  BIBLIOTECARIO O GERENTE
    Todo lo demas de gestion: prestamos, devoluciones, multas, ejemplares,
    usuarios, sanciones, turnos, reportes, cierre diario, dashboard,
    catalogo (escritura), reservas (cola), avisos, libros virtuales (escritura),
    /api/comprobantes/** (revisar los comprobantes de Yape), /api/cajas/** y
    /api/almacenamiento/** (estado y prueba de escritura de R2).

  Cualquier ruta no listada: solo exige estar autenticado.

  CSRF desactivado (la API es stateless con JWT, no usa cookies de sesion).
  Contrasenas cifradas con BCrypt.

CORS

  Los origenes permitidos vienen de cors.allowed-origins en
  application.properties. Incluye el 5173 (Vite), el 4200 (Angular), el 3000
  (CRA) y el 5500 (Live Server). Se expone la cabecera Content-Disposition para
  que el navegador pueda leer el nombre de los archivos CSV.


--------------------------------------------------------------------------------
8. COMO FUNCIONA CADA MODULO
--------------------------------------------------------------------------------

8.1 INICIO DE SESION Y REGISTRO
    POST /api/auth/login. Toda la logica vive en AuthService: busca el usuario,
    compara la contrasena con BCrypt, verifica que la cuenta este activa y, si
    es lector, que no tenga sancion vigente. Solo si las tres comprobaciones pasan
    emite el token. Un fallo es 401 (credenciales o cuenta inactiva) o 403
    (sancion vigente).
    El lector puede registrarse solo en /registro, y siempre se crea con rol
    LECTOR. No existe ninguna forma por API de crear un bibliotecario o un
    gerente: esos cuentas vienen de data.sql. El frontend hace auto-login
    despues del registro.

8.2 CATALOGO (libros, autores, generos)
    El catalogo es publico: se puede consultar sin iniciar sesion.
    El estado de cada libro se deriva de sus ejemplares (ver seccion 6).
    El ISBN se normaliza antes de comparar: se quitan guiones y espacios, y se
    pasa a mayusculas. Si se repite, la API devuelve 409.
    Un autor con libros asociados no se puede eliminar (devuelve 400).
    Los generos solo se pueden crear y listar: no hay PUT ni DELETE.

    ALTA DE UN LIBRO Y DE SUS COPIAS.
    "Libros" -> "+ Registrar libro" es el alta del LIBRO ORIGINAL: ficha,
    autores, generos, tipo, precios, portada y (si es virtual o hibrido) el
    PDF. El formulario NO crea copias: las COPIAS (ejemplares) son el
    inventario fisico y se dan de alta en el modulo "Ejemplares":
      - "+ Registrar ejemplar": se elige el libro y se escribe el codigo de
        inventario (se escanea con la camara o con el lector fisico), la
        ubicacion y el precio propio de la copia;
      - desde la ficha del libro -> "Agregar o editar copias", que abre
        "Ejemplares" con el libro ya elegido.
    Un libro se puede registrar aunque todavia no tenga ninguna copia: sin
    ejemplares no se puede prestar (se reserva), pero la obra queda en el
    catalogo. Un libro VIRTUAL no admite ejemplares: sus copias son el PDF.

    ESCANER DE ISBN.
    GET /api/libros/isbn/{isbn} busca el libro en Google Books y, si no esta,
    en Open Library, y devuelve titulo, autores, editorial, anio, paginas y la
    portada (que se descarga y se guarda en R2). No necesita clave: son APIs
    publicas. El ISBN viaja como texto; el navegador es quien lee el codigo de
    barras (ver 12.8).

8.3 EJEMPLARES (copias fisicas)
    Cada ejemplar tiene un codigo_inventario unico e inmutable, una ubicacion
    en la estanteria y un estado.
    El estado lo cambia el sistema automaticamente con los prestamos, las
    devoluciones y las reservas. A mano solo se pueden poner estos cuatro:
    DISPONIBLE, DANADO, PERDIDO, BAJA.
    Mientras un ejemplar esta PRESTADO o RESERVADO, no se puede editar.
    Al dar de alta un ejemplar nuevo, si hay cola de reservas de ese libro,
    el ejemplar se aparta automaticamente para el primero de la cola.

    PRECIO POR COPIA. Cada copia puede tener su propio precio (ejemplares.precio),
    que puede ser distinto del precio general del libro: una edicion antigua,
    un ejemplar de un tiraje raro. Si la copia no tiene precio, se usa el
    precio de compra del libro. El precio se puede poner en dos sitios:
      - en "Ejemplares" -> "+ Registrar ejemplar" -> "Precio de esta copia";
      - en "Ejemplares" -> ver detalle -> "Precio de esta copia".
    Ese precio es el que se COPIA al prestamo (ver 8.4).

8.4 PRESTAMOS  (el flujo mas importante)
    Solo el bibliotecario o el gerente. Se hace con un asistente de tres pasos
    en la interfaz: Usuario -> Libro -> Confirmar.

    Que hace el sistema, en orden:
      1. Verifica que el lector exista, sea LECTOR y este activo.
      2. Si tiene sancion activa -> error 403.
      3. Si tiene multas PENDIENTES o PARCIALES -> error 400.
      4. Elige el ejemplar:
           a) si el lector tiene una reserva NOTIFICADA de ese libro, se le da
              ese ejemplar (el que estaba apartado para el);
           b) si no, el primer ejemplar DISPONIBLE, por id mas bajo;
           c) si no hay ninguno -> error 400, y el mensaje le dice al lector
              que puede reservarlo.
      5. Si el ejemplar estaba RESERVADO, comprueba que la reserva notificada
         sea de este mismo lector. Si es de otro -> error 400 "apartado para
         otro lector". Esto es lo que protege la cola FIFO.
      6. Calcula la fecha de vencimiento y guarda la tarifa:
           FECHA_FIJA -> vence en "dias_max" dias (7 por defecto) y COPIA la
                         tarifa vigente en el prestamo;
           ABIERTO   -> no tiene fecha de vencimiento ni tarifa.
      6.b COPIA tambien el precio del ejemplar (prestamos.precio_copia), igual
           que la tarifa: es el precio de esa copia en el momento del prestamo.
           Si la copia no tiene precio propio, se copia el precio del libro.
           Vale igual en las dos rutas: con fecha fija y abierto. Cambiar el
           precio del libro o de la copia despues no altera los prestamos ya
           hechos.
      7. El ejemplar pasa a PRESTADO y el prestamo a ACTIVO.

8.5 DEVOLUCIONES Y MULTAS
    Se busca el prestamo, se indica en que estado quedo el ejemplar
    (DISPONIBLE, DANADO, PERDIDO o BAJA) y opcionalmente observaciones.

      - Dias de retraso = dias completos despues de la fecha de vencimiento.
        Si se devuelve a tiempo, cero.
      - Si el prestamo era ABIERTO, los dias de retraso son SIEMPRE cero, aunque
        pase el tiempo. Esa es una regla del documento del proyecto.
      - Si hay retraso, se crea la multa:
            monto = dias de retraso x tarifa
        La tarifa se lee del PRESTAMO (la copia que se hizo al prestar), nunca
        de la configuracion actual. Por eso cambiar la tarifa despues no altera
        multas ya calculadas.
      - Se notifica al lector de la multa pendiente (si el interruptor
        aviso_multa esta activo).
      - Si el ejemplar vuelve DISPONIBLE, entra a la cola de reservas.
        Si vuelve DANADO, PERDIDO o BAJA, se queda asi y no se asigna a nadie.

8.6 RESERVAS (cola FIFO)
    Solo el lector puede crear reservas.
      - Solo se reserva un libro que NO tenga ejemplares DISPONIBLES.
        Si tiene uno disponible, la API responde 400 diciendo que pida el
        prestamo en mostrador.
      - No se puede tener dos reservas activas del mismo libro (400).
      - No se puede reservar un libro que ya se tiene prestado (400).
      - Un lector con sancion activa no puede reservar (403).

    Como avanza la cola:
      - Al liberarse un ejemplar, se busca la reserva PENDIENTE mas antigua
        (orden: fecha de reserva, luego id). Se le asigna, el ejemplar queda
        RESERVADO, se le notifica y tiene "dias_retiro_reserva" dias (2 por
        defecto) para recogerlo.
      - Solo se entrega UN ejemplar por devolucion. Si hay dos personas
        esperando, la segunda sigue esperando.
      - Si no recoge a tiempo, la tarea de las 00:10 la marca VENCIDA, avisa y
        pasa el ejemplar al siguiente de la cola.
      - Si cancela una reserva que ya tenia un ejemplar asignado, el ejemplar
        vuelve a la cola para el siguiente.
      - Cancelar: el lector solo puede cancelar las suyas; el personal puede
        cancelar cualquiera.

8.7 SANCIONES
    Solo se puede sancionar a un LECTOR (400 si no). La fecha_fin no puede ser
    anterior a hoy (400).
    Una sancion esta vigente mientras no se haya levantado y la fecha_fin sea
    mayor o igual a hoy. Se caduca sola por fecha: no hay tarea que la limpie,
    el estado se calcula al consultar.
    Mientras esta vigente, el lector NO puede: iniciar sesion, pedir prestamos,
    ni hacer reservas.
    El bibliotecario o el gerente puede levantarla antes de tiempo.
    Nota: pagar la multa NO levanta la sancion automaticamente.

8.8 PAGOS DE MULTA
    El personal registra el pago: monto, metodo y fecha.
      - El monto debe ser mayor que 0.
      - No puede superar el saldo pendiente.
      - Si el saldo queda en 0, la multa pasa a PAGADA.
      - Si sobra saldo, pasa a PARCIAL.
    DESCUENTO POR EFECTIVO. Pagar en efectivo da 15% de descuento sobre el monto
    que se salda. El cajero digita la deuda que quiere liquidar (por ejemplo
    S/ 10.00) y la biblioteca cobra solo el 85%: entran S/ 8.50 a la caja y los
    S/ 1.50 restantes quedan registrados en pagos_multa.descuento. La deuda se
    reduce por el monto completo (10.00), no solo por el efectivo recibido, asi
    que la multa queda PAGADA sin residuo. En cualquier otro metodo de pago se
    cobra el monto completo y el descuento es cero. El descuento tambien aplica
    a los pagos parciales en efectivo: al pagar S/ 5.00 de un saldo de S/ 10.00
    entran S/ 4.25 y se condonan S/ 0.75, dejando un saldo de S/ 5.00.
    Cada pago queda registrado con el bibliotecario que lo recibio (auditoria).
    Un lector con multa PENDIENTE o PARCIAL no puede pedir prestamos nuevos.

8.9 AVISOS Y NOTIFICACIONES
    El lector ve sus propios avisos y los marca como leidos (uno o todos).
    El personal puede crear avisos: a un usuario concreto o a todos los lectores
    (si se omite id_usuario).
    Los interruptores de la tabla configuracion (aviso_vencimiento,
    aviso_reserva, aviso_multa, aviso_nuevo_libro) apagan cada tipo.
    Los avisos automaticos no se duplican: el sistema comprueba si ya se envio
    uno igual el mismo dia (o en los ultimos 7 dias para recordatorios de multa).

8.10 BITACORA DE TURNOS
    Cada bibliotecario abre su turno y lo cierra con observaciones.
    No se puede abrir un turno si ya tiene otro abierto (400).
    El gerente ve la bitacora de todo el personal; el bibliotecario solo la suya.

8.11 CIERRE DIARIO
    Registra el total de prestamos activos y vencidos del dia.
    Se puede hacer a mano o automaticamente a las 23:55.
    Si ya se hizo, devuelve 409. Si hay turnos sin cerrar, devuelve 400.

8.12 REPORTES
    Tres reportes: inventario, prestamos (con ranking de los mas prestados) y
    multas (con recaudacion por metodo de pago). Cada uno se descarga en CSV.
    El CSV usa separador punto y coma, lleva BOM UTF-8 y saltos CRLF, para que
    Excel lo abra bien en español.

8.13 CONFIGURACION (versionado, solo gerente)
    Hay dos tablas con propositos distintos:
      - configuracion: fila unica con nombre, horarios e interruptores.
        Se actualiza en el sitio, solo los campos que llegan.
      - configuracion_multas: tarifa diaria, dias maximos de prestamo y dias
        para recoger una reserva. Es un historial: cada vez que cambian esos
        tres valores, se cierra la vigencia actual (fecha_vigencia_hasta) y se
        inserta una fila nueva (fecha_vigencia_desde) con el gerente que lo hizo.
    GET /api/configuracion/historial devuelve todas las versiones.
    Valores por defecto si no hay ninguna: tarifa 1.00, 7 dias, 2 dias de retiro.

8.14 LIBROS VIRTUALES (portadas en R2, PDF privado, alquiler y compra)
    Un libro puede ser FISICO (se presta), VIRTUAL (solo PDF) o HIBRIDO (las dos).
    El alta del libro la hace el bibliotecario en un solo envio (multipart):

      POST /api/libros/completo     ("libro" en JSON + portada + pdf)

    - El formulario empieza escaneando el ISBN: GET /api/libros/isbn/{isbn}
      pregunta A LA VEZ a Google Books y a Open Library y junta lo que diga cada
      una (si con el ISBN-13 no hay nada, prueba el ISBN-10 equivalente). Devuelve
      titulo, autores, editorial, anio, paginas, generos y la portada, ya cruzados
      con la biblioteca: los autores y generos que existen se marcan solos y los
      que no, se crean al guardar el libro (sin importar tildes ni mayusculas, asi
      no se repiten). Si el ISBN ya esta registrado, el formulario lo avisa y
      ofrece "Agregar copias".
    - La portada se baja y se guarda en Cloudflare R2 (bucket publico
      "biblioteca"): la base de datos solo guarda el link (url_imagen), nunca la
      imagen. Es OPCIONAL: si no se pudo bajar, el libro se guarda igual y sale un
      aviso para subirla a mano. Si el .env no tiene R2_URL_PUBLICO_PORTADAS, las
      portadas las sirve el backend (GET /api/archivos/portadas/...) y se ven igual.
    - El alta es solo de la OBRA ORIGINAL: las copias (ejemplares) se dan de
      alta aparte, en el modulo Ejemplares, y quedan agrupadas bajo el libro,
      con su codigo_inventario, ubicacion, estado y su propio precio. Un libro
      de 5 copias es un libro con 5 ejemplares: solo los ejemplares se reservan
      y se prestan.
    - VIRTUAL e HIBRIDO necesitan el PDF, que va a un bucket PRIVADO
      ("biblioteca-pdfs"). La clave (clave_pdf) tampoco sale de la base: el
      lector recibe una URL firmada que caduca en 15 minutos.
    - Precios: el fisico solo tiene precio de venta. El virtual y el hibrido
      tienen precio de alquiler por dia y precio de compra (el de compra tiene
      que ser mayor que un dia de alquiler).

    El lector pide el libro y paga:

      POST /api/accesos-virtuales   { id_libro, tipo, dias_alquiler, metodo_pago }

      - ALQUILER: se guarda dias_alquiler y, al cobrarse, fecha_fin = hoy + dias.
      - COMPRA: sin vencimiento; el que lo compro puede descargar el PDF.
      - GET /api/accesos-virtuales/{id}/pdf devuelve la URL firmada (15 min) para
        LEER (el navegador lo abre). Con ?descargar=true el navegador lo GUARDA:
        solo si es una compra (o lo pide el personal); un alquiler da 403.
      - El precio se COPIA en el pedido (igual que la tarifa de multa en el
        prestamo): cambiar el precio del libro no altera los pedidos ya hechos.

    Formas de pago:

      YAPE             El lector ve el QR ESTATICO de Yape Negocio
                      (src/main/resources/qr.jpeg, servido por
                      GET /api/pagos/yape/qr) con el MONTO escrito debajo.
                      Paga en su app de Yape tecleando ese monto (el QR no lo
                      lleva), saca una captura de pantalla y la sube como
                      COMPROBANTE. El personal la ve (con una URL firmada que
                      caduca en 15 minutos) y la ACEPTA o la DENIEGA. Al
                      aceptarla, el pedido queda pagado, el lector recibe el
                      aviso de "felicidades, compra exitosa" y ya puede abrir
                      el PDF.
                      OJO: no existe una API publica de Yape con un QR dinamico
                      y el monto exacto. Si en el futuro el QR tiene que llevar
                      el monto ya puesto, hay que contratar a un adquirente
                      (Niubiz, Nuvei, PayU, Kushki, EBANX, YabandPay...) que
                      cobra 3.5 a 5 por ciento mas S/0.30 por operacion.
                      El comprobante NO cambia el estado del pedido: sigue en
                      PENDIENTE_PAGO hasta que alguien lo acepta. "En revision"
                      es un estado DERIVADO del ultimo comprobante, no un
                      estado nuevo del pedido.
      EFECTIVO        Se genera un numero de cola y un CODIGO DE RETIRO de 8
                      caracteres que vale 24 horas. El cliente lo muestra en el
                      mostrador y el personal busca la orden con ese codigo.

    El panel del mostrador ("Reservas de libro virtual", personal) tiene una
    seccion de COMPROBANTES YAPE (los que estan esperando revision: ver la
    captura, aceptar o denegar) y tres decisiones sobre una orden pendiente:
    CANCELARLA, CAMBIARLA (de alquiler a compra, o cambiar los dias) o
    COBRARLA. Al cobrarla se activa el acceso y el lector recibe el aviso:
    "Felicidades, compra exitosa..." o "Felicitudes, tu alquiler esta listo.
    Disfruta tus N dias restantes."

    El lector ve sus libros en su perfil ("Mis libros virtuales"). Un alquiler
    vencido sale en plomo: no se puede abrir hasta volver a reservarlo.

8.15 LA CAJA (el cajon de dinero)
    Es UNA SOLA caja para toda la biblioteca: solo puede haber una abierta a la
    vez y el personal se turna. No lleva turno_id: la caja es la sesion de
    dinero y el turno es la bitacora del personal; conviven, no se mezclan.

      - Al abrirla se registra el MONTO INICIAL (el dinero que ya estaba en el
        cajon). Si ya hay una caja abierta, la API responde 400.
      - El dinero SOLO entra por los movimientos. CajaService.registrar() es el
        unico punto por donde pasa: si no hay caja abierta, el cobro se rechaza
        con un mensaje claro ("No hay ninguna caja abierta..."). Por eso el
        total del dia es exactamente la suma de los movimientos del dia.
      - Al cerrarla se cuenta el efectivo y el sistema lo compara con lo
        esperado (monto inicial + movimientos en efectivo): dice si sobra o
        falta. Lo cobrado con Yape o tarjeta no esta en el cajon, asi que no
        cuenta en el arqueo.
      - Que pasa por la caja:
          COBRO_PRESTAMO_FECHA_FIJA / COBRO_PRESTAMO_ABIERTO
              Al devolver un prestamo se cobra el PRECIO DE LA COPIA (el que
              se copio al prestamos). Los dos tipos de prestamo se cobran igual.
              Si la copia no tiene precio, no hay cobro: la devolucion se
              registra igual, sin caja y sin movimiento.
          COBRO_MULTA
              El efectivo REALMENTE recibido (con el 15% de descuento de
              efectivo). El monto condonado queda en el descuento del
              movimiento.
          VENTA_VIRTUAL / ALQUILER_VIRTUAL
              Al cobrar un pedido de libro virtual en el mostrador, o al
              aprobar un comprobante de Yape (este ultimo entra como YAPE).
      - La columna caja_abierta es UNIQUE (1 mientras esta abierta, NULL al
        cerrarla): la propia base impide que haya dos cajas abiertas.
      - La pantalla Caja tiene tres pestanas: HOY (monto inicial, cobrado,
        total del dia, desglose por metodo y por tipo, y los movimientos),
        PENDIENTES (prestamos sin devolver con su precio a cobrar, reservas
        por recoger con su fecha limite, reservas en cola y pedidos virtuales
        sin cobrar) e HISTORIAL (las cajas cerradas con su arqueo).

8.16 AUDITORIA (que hizo cada uno)
    Cada accion del personal queda anotada con su autor, su fecha y una frase
    legible. Solo la ve el GERENTE: GET /api/auditoria, con filtros de fechas,
    de persona y de tipo de accion, y paginado.

      Se auditan: alta y edicion de libros, escaneo de ISBN, subida de portada
      y de PDF, alta y edicion de ejemplares, registro de prestamos y de
      devoluciones, cobro de multas, apertura y cierre de caja, apertura y
      cierre de turno, aceptacion y denegacion de comprobantes de Yape, cobro
      de pedidos virtuales, alta y edicion de usuarios, sanciones levantadas y
      aplicadas, creacion de avisos y cambio de configuracion.

      La auditoria se escribe EN LA MISMA TRANSACCION que la operacion: si la
      operacion se revierte, la auditoria tampoco se guarda. No queda rastro de
      algo que no pasó. Por eso el metodo que audita no puede ser de solo
      lectura.


--------------------------------------------------------------------------------
9. TAREAS PROGRAMADAS
--------------------------------------------------------------------------------

Tres tareas automaticas, todas con zona horaria America/Lima:

  00:10  Vencer las reservas notificadas que no se recogieron a tiempo,
         avisar al lector y pasar el ejemplar al siguiente de la cola.

  08:00  Generar avisos de vencimiento proximo (vence manana), prestamos
         vencidos y recordatorios de multa pendiente.

  23:55  Generar el cierre diario del dia, si nadie lo hizo manualmente.


--------------------------------------------------------------------------------
10. COMO SE DEVUELVEN LOS ERRORES
--------------------------------------------------------------------------------

Un solo manejador global (GlobalExceptionHandler) convierte las excepciones en
JSON. El frontend los muestra con mensajeError().

Formato de la respuesta de error:

  {
    "timestamp": "2026-10-01T18:30:00",
    "status": 409,
    "error": "Conflict",
    "mensaje": "Ya existe un libro con ese ISBN",
    "path": "/api/libros",
    "errores": { "isbn": "no debe estar vacio" }
  }

  El campo "errores" solo aparece cuando falla una validacion @Valid.

Que exception produce cada codigo:

  400  OperacionInvalidaException, EjemplarNoDisponibleException,
       ReservaActivaDuplicadaException, TurnoPendienteException,
       validaciones @Valid, JSON mal formado, parametros con tipo erroneo.
  401  CredencialesInvalidasException. Tambien sin token o con token vencido.
  403  UsuarioSancionadoException, AccesoDenegadoException, rol sin permiso.
  404  RecursoNoEncontradoException, ruta inexistente.
  405  Metodo HTTP no permitido.
  409  RecursoDuplicadoException, violacion de unicidad en la base.
  500  Cualquier otro error (se registra en el log).


--------------------------------------------------------------------------------
11. PAGINACION
--------------------------------------------------------------------------------

Solo tres endpoints devuelven paginas:
  GET /api/usuarios
  GET /api/notificaciones/todas
  GET /api/auditoria

Formato:

  { "contenido": [ ... ], "pagina": 0, "tamanio": 10,
    "total_elementos": 57, "total_paginas": 6 }

  La pagina empieza en 0. El tamanio por defecto es 10 y el maximo 100.

Todos los demas endpoints de listado devuelven el arreglo completo, sin paginar.


--------------------------------------------------------------------------------
12. EL FRONTEND EN DETALLE
--------------------------------------------------------------------------------

12.1 ARRANQUE
    React.StrictMode -> App -> ToastProvider -> AuthProvider -> AppRoutes.
    AppRoutes contiene el BrowserRouter. Las 23 pantallas enrutadas (mas tres
    sub-pantallas que se abren dentro de otras: FormLibro, PedidoVirtual y
    FormSancion) se importan de forma estatica: no hay carga perezosa ni
    limites de error.

12.2 SESION
    AuthContext guarda { token, usuario }. De ahi salen tres banderas:
      esLector  = rol === 'LECTOR'
      esAdmin   = rol es BIBLIOTECARIO o GERENTE
      esGerente = rol === 'GERENTE'
    "Recordar sesion" decide si se guarda en localStorage o en sessionStorage.
    Al leer, se prueba primero localStorage y luego sessionStorage.

12.3 PETICIONES HTTP
    Todo pasa por api/axiosClient.js:
      - Agrega Authorization: Bearer <token> en cada peticion.
      - Si la API responde 401 (salvo en el login), borra la sesion y manda
        a /login?expirada=1.
      - mensajeError() traduce el error del backend a un texto: primero intenta
        el mapa "errores" (campo: mensaje) y si no, el campo "mensaje".

12.4 RUTAS
    /login, /registro                          publicas
    /inicio, /libros, /autores, /prestamos,
    /reservas, /avisos, /perfil,
    /mis-libros-virtuales                     cualquier usuario autenticado
    /usuarios, /devoluciones, /ejemplares,
    /pagos-multa, /libros-virtuales, /sanciones,
    /caja, /bitacora, /reportes                bibliotecario o gerente
    /configuracion, /auditoria                 solo gerente
    /                                          redirige a /inicio
    cualquier otra ruta                        redirige a /inicio

    /prestamos y /reservas muestran una pantalla distinta segun el rol, pero
    en la misma URL: el lector ve "Mis prestamos" / "Mis reservas", el personal
    ve el modulo completo.

    ProtectedRoute hace tres comprobaciones en orden: sin sesion va a /login;
    rol insuficiente va a /inicio, sin mostrar ningun mensaje.

12.5 MENU LATERAL
    LECTOR (7 entradas): Inicio, Libros, Autores, Mis prestamos y multas,
                          Mis reservas, Mis libros virtuales, Avisos.
    BIBLIOTECARIO / GERENTE, bloque general (7): Inicio, Libros, Autores,
                          Usuarios, Prestamos, Devoluciones, Reservas.
    BIBLIOTECARIO / GERENTE, bloque Administracion (8): Ejemplares, Reservas de
                          libro virtual, Pagos de multas, Caja, Sanciones y
                          bloqueos, Avisos, Bitacora de turnos, Reportes y cierre.
    Solo GERENTE: Auditoria y Configuracion, dentro del bloque de
                   Administracion.

12.6 HOOKS DE DATOS (src/hooks.js)
    useApi(funcion, deps, valorInicial) -> { datos, setDatos, cargando, recargar }
        Carga automatica al montar y cada vez que cambian las dependencias.
        Maneja el 204 devolviendo null.
        Si falla, muestra un toast y NO relanza el error: no devuelve un campo
        de error, asi que una peticion fallida se ve igual que una vacia.
    useAccion() -> { ejecutar, devolver, enviando }
        Envuelve una escritura: muestra toast de exito o de error, y devuelve
        el dato o null.
    useDebounce(valor, ms) -> valor
        Retrasa la propagacion de un valor, para no llamar a la API en cada
        tecla.

12.7 DISENO VISUAL
    global.css define las variables de color y tipografia tomadas del
    prototipo de Figma: azul marino de marca, fondo crema, tipografias Lora
    (titulos) e Inter (texto), y cuatro pares de colores semanticos
    (verde, rojo, ambar, azul) que corresponden a los cuatro colores de Badge.
    Portada.jsx usa la portada real guardada en R2 (libro.url_imagen) y, si
    todavia no hay o la imagen no carga, cae en la portada generada: un degrade
    de color calculado a partir del id.
    Debajo de 860 px de ancho el menu lateral se convierte en una barra
    horizontal y las columnas se apilan.

12.8 ESCANER DE CODIGOS (components/common/EscanerCodigo.jsx)
    Un solo componente sirve para los tres casos: el ISBN de un libro nuevo,
    el codigo de inventario de una copia y el codigo de una etiqueta.

      En la PC:   el lector de codigos de barras actua como teclado. Se pasa
                  el codigo y se pulsa Enter; el campo se rellena solo.
      En el celular: el boton de la camara abre el visor y lee el codigo.

    La camara usa la API nativa del navegador (window.BarcodeDetector) con
    getUserMedia: no se anade ninguna libreria. Formatos: ean_13, ean_8,
    upc_a, code_128 y qr_code.

    OJO, dos limites del navegador:
      - hace falta un contexto seguro: https o localhost. En la IP del VPS
        (http://...) la camara NO se puede abrir todavia;
      - Firefox no implementa BarcodeDetector. Ahi el componente avisa y se
        sigue con el lector fisico o escribiendo el codigo a mano.
    Cuando el codigo no se puede leer, no se rompe nada: el formulario sigue
    esperando que se escriba a mano.


--------------------------------------------------------------------------------
13. LAS REGLAS DE NEGOCIO, EN UNA LISTA
--------------------------------------------------------------------------------

  1.  Un prestamo es de FECHA_FIJA o ABIERTO.
  2.  Un prestamo ABIERTO nunca genera multa, nunca.
  3.  La multa es dias de retraso por la tarifa, y la tarifa se COPIA en el
      prestamo: cambiar la tarifa despues no altera lo ya calculado.
  4.  Solo se presta si hay un ejemplar DISPONIBLE. Si no hay, se reserva.
  5.  Solo se reserva un libro sin ejemplares disponibles.
  6.  Las reservas son FIFO estricto (fecha de reserva, luego id).
  7.  Al liberarse un ejemplar se aparta para el primero de la cola y se le
      avisa con una fecha limite para recogerlo.
  8.  La reserva vence si no se recoge a tiempo y pasa al siguiente.
  9.  El lector con sancion activa no puede entrar, ni prestar, ni reservar.
  10. El lector con multa pendiente o parcial no puede pedir prestamos.
  11. Un lector no puede tener dos reservas activas del mismo libro.
  12. No se elimina un autor que tenga libros.
  13. No se elimina un ejemplar con historial de prestamos.
  14. El codigo de inventario es unico e inmutable.
  15. Las contrasenas se guardan cifradas con BCrypt, nunca en texto plano.
  16. El acceso se restringe por rol.
  17. El estado del libro y del usuario se calculan, no se guardan.
  18. Cada dia se genera el cierre con los prestamos activos y vencidos.
  19. Pagar en efectivo aplica 15% de descuento: el monto digitado es la deuda
      que se salda y la biblioteca cobra el 85%.
  20. Un libro es FISICO, VIRTUAL o HIBRIDO. El virtual y el hibrido necesitan
      PDF. El fisico NO necesita ejemplares para registrarse: sus copias se
      dan de alta aparte, en el modulo Ejemplares, y puede no tener ninguna
      todavia. Un libro virtual no admite ejemplares (sus copias son el PDF).
  21. El precio de un pedido de libro virtual se COPIA en el pedido: cambiar el
      precio del libro no altera lo ya pedido.
  22. El codigo de retiro de una orden pagada en tienda vale 24 horas y esta
      atado a esa cuenta. Al cobrar la orden, el codigo se anula.
  23. El PDF de un libro virtual se entrega con una URL firmada que caduca en
      15 minutos, solo a quien lo compro o alquilo (o al personal).
  24. Un alquiler de libro virtual vence por fecha_fin: despues sale en plomo y
      no se puede abrir hasta volver a reservarlo.
  25. Las imagenes y los PDF viven en Cloudflare R2, no en la base de datos: en
      la base solo queda el link (portada) o la clave del objeto (PDF).
  26. El precio del EJEMPLAR se COPIA en el prestamo (como la tarifa de multa):
      vale igual en las dos rutas, fecha fija y abierto. Si la copia no tiene
      precio propio, se copia el del libro.
  27. El estado de un PEDIDO de libro virtual SI se guarda: es el ciclo de vida
      del pago. Lo que se deriva es si el alquiler ya vencio.
  28. La caja es UNA SOLA para toda la biblioteca: solo puede haber una abierta,
      se registra el MONTO INICIAL y al cerrarla se hace el arqueo (el efectivo
      contado contra el esperado).
  29. Al devolver un prestamo (de fecha fija O abierto) se cobra el PRECIO DE
      LA COPIA. La multa se cobra aparte, con su 15% de descuento en efectivo.
  30. No se cobra sin caja abierta: el backend rechaza el cobro. La devolucion
      de una copia sin precio si se registra (no hay nada que cobrar).
  31. El pago con Yape es QR estatico + COMPROBANTE: el personal aprueba o
      deniega la captura. El pedido NO cambia de estado al subirlo ("en
      revision" es un estado derivado).
  32. Las acciones del personal quedan auditadas (quien hizo que). Solo el
      gerente las ve, y la auditoria se guarda en la MISMA transaccion que la
      operacion: si la operacion se revierte, la auditoria tampoco.


--------------------------------------------------------------------------------
14. COMO CAMBIAR COSAS SIN ROMPER NADA
--------------------------------------------------------------------------------

Anadir un endpoint nuevo:
  1. Crea el record DTO en dto/ (nombres: Registro*, Actualizar*, Nuevo*,
     *ResponseDTO).
  2. Escribe la consulta en repository/ si hace falta.
  3. Pon la regla de negocio en service/, con @Transactional.
  4. Agrega el metodo en controller/ delegando al service/.
  5. Si necesita permiso, anadelo a SecurityConfig en el orden correcto.
  6. Si puede fallar, lanza una excepcion de exception/ con el codigo correcto.
  7. Agrega la funcion en frontend/src/api/ segun el recurso.
  8. Si es una pantalla nueva, crea pages/Nombre/, registrala en AppRoutes
     con su nivel de acceso, y agrega el enlace en Sidebar si aplica.

Cambiar una regla de negocio:
  - El sitio unico es service/. Si la regla tambien depende de una tabla de
    configuracion, esa parte va en ConfiguracionService.


--------------------------------------------------------------------------------
15. LO QUE NO EXISTE
--------------------------------------------------------------------------------

  - No hay una sola prueba automatica. La carpeta src/test/java/com/biblioteca
    esta vacia, y en el pom.xml hay dependencias de prueba (H2,
    spring-boot-starter-test, spring-security-test) que no usa nadie.
  - No hay integracion continua ni archivo de pruebas.
  - No hay lint, ni formateador, ni editorconfig.
  - No hay carga perezosa de pantallas ni limites de error en React.
  - No hay refresco de token ni lista de revocacion: si un token sigue vivo,
    sigue valiendo hasta que pasan las 8 horas.
  - No hay envio de correos ni recuperacion de contrasena. El enlace
    "olvide mi contrasena" de la pantalla de login es solo informativo.
  - No hay API para crear bibliotecarios ni gerentes.
  - No hay Docker, ni despliegue automatizado, ni variables de entorno para
    los secretos. (Los secretos de Cloudflare R2 SI van en un .env, que no se
    sube al repositorio: application.properties los lee con
    spring.config.import=optional:file:.env[.properties].)
  - No hay aplicacion movil todavia: la version React Native (APK) es la regla
    15 del AGENTS.md y se hace al final, cuando el sistema web este cerrado.
  - No hay Swagger/OpenAPI en el codigo (no hay dependencia de springdoc), aunque
    el AGENTS.md lo mencione en la lista de pasos de un endpoint nuevo.


--------------------------------------------------------------------------------
16. RIESGOS CONOCIDOS
--------------------------------------------------------------------------------

  - CONCURRENCIA YA CORREGIDA. Antes, dos peticiones simultaneas para el mismo
    ejemplar podían registrarse las dos. Ahora la eleccion del ejemplar del
    prestamo, la cabeza de la cola FIFO de reservas y el cobro de una multa usan
    bloqueo pesimista de escritura (@Lock(PESSIMISTIC_WRITE)). Verificado con 8
    prestamos simultaneos del mismo ejemplar: 1 aceptado y 7 rechazados con 400.
    Si anades una consulta que lea y luego escriba el estado de un ejemplar o de
    una multa, bloqueala igual.
  - Solo dos endpoints paginan; el resto carga el catalogo entero en memoria.
  - El filtro VENCIDOS de prestamos se aplica en Java, no en SQL.
  - Hay consultas N+1: al listar usuarios se hacen dos consultas por usuario.
  - (Corregido) useAccion trataba un 204 sin cuerpo como fallo. Ahora un 2xx
    sin cuerpo devuelve true.
  - (Corregido) El catalogo hacia 3 consultas por libro (copias, disponibles y
    libros por autor). Ahora son dos consultas en bloque para toda la lista.
  - Los secretos estan escritos en el codigo: la clave de MySQL y la clave de
    firma del JWT estan en application.properties.
  - spring.jpa.show-sql esta activado, lo que imprime todo el SQL.
  - DESPLIEGUE: Dockerfile en la raiz (backend + frontend en un contenedor) y
    MySQL como recurso aparte en Coolify. Ver DESPLIEGUE-COOLIFY.md. El
    personal puede reservar a nombre de un lector en Reservas > "+ Nueva
    reserva" (POST /api/reservas/mostrador). Un libro virtual no se reserva.
  - LA CAMARA DEL ESCANER NECESITA HTTPS. La API BarcodeDetector solo funciona
    en un contexto seguro: https o localhost. El VPS se entra por IP (http://),
    asi que ahi la camara NO se podra abrir todavia: se usaria el lector
    fisico o se escribiria el codigo a mano. Firefox tampoco la soporta.
  - El backend hay que REINICIARLO para que carguen las rutas nuevas. Si el
    frontend avisa "El backend no tiene la ruta del escaner", casi siempre es
    que el proceso de Java sigue con el codigo viejo: Ctrl+C y "mvn
    spring-boot:run" otra vez.
  - YAPE NO TIENE API PUBLICA. La regla del proyecto pide "un QR de Yape con el
    monto exacto", y eso no se puede hacer con una API abierta: solo un
    adquirente (Niubiz, Nuvei, PayU, Kushki, EBANX, YabandPay, TAYPI) puede
    generar un QR dinamico, y cobra 3.5 a 5 por ciento mas S/0.30 por
    operacion. Lo que si se implemento: el QR ESTATICO de Yape Negocio
    (src/main/resources/qr.jpeg) con el monto escrito debajo, y el lector
    sube la captura del pago como comprobante; el personal la aprueba o la
    deniega. Si el proyecto necesita el QR con el monto ya puesto, hay que
    contratar al adquirente. (Mercado Pago ya no se usa: no hay checkout de
    tarjeta para libros virtuales.)
  - El PDF de los libros virtuales esta en el bucket privado "biblioteca-pdfs".
    Si ese bucket no existe, el alta de un libro virtual falla: se crea desde
    la propia aplicacion, en Configuracion > Almacenamiento > "Preparar
    almacenamiento" (POST /api/almacenamiento/preparar, solo gerente), que
    llama a la API de S3 de R2 con las mismas credenciales del .env. Si el token
    no tiene permiso para administrar buckets, hay que crearlo a mano en el
    panel de Cloudflare R2. Lo mismo pasa con las capturas de los comprobantes
    de Yape: van al mismo bucket privado, en la carpeta "comprobantes/".
  - La URL publica de las portadas (R2_URL_PUBLICO_PORTADAS) NO se puede
    deducir de la cuenta: hay que copiarla del panel de Cloudflare (R2 > bucket
    "biblioteca" > Settings > Public Development URL). Sin ella las portadas
    SI se ven, pero las sirve el backend (GET /api/archivos/portadas/...). Para
    el VPS conviene ponerla: asi Cloudflare entrega las imagenes y el backend no
    gasta nada. Las portadas ya guardadas pasan solas a la URL nueva.
  - La tabla auditorias NO se purga sola: crece con cada accion del personal.
    Si hay que limpiarla, se borra a mano y solo las filas antiguas.

--------------------------------------------------------------------------------
17. DIFERENCIAS ENTRE LA DOCUMENTACION Y EL CODIGO
--------------------------------------------------------------------------------

Conviene saberlo para no perder tiempo:

  1. La contrasena de MySQL. LEEME.md y COMO-EJECUTAR.md dicen "sebas123";
     application.properties tiene "denis123". El valor correcto es denis123.

  2. El documento academico del proyecto (el .docx de 14 tablas) describe un
     modelo que el codigo cambio a proposito:
       - "libros_generos" ahi es un texto libre; en el codigo es una tabla
         generos con id, para no repetir-generos mal escritos.
       - "estado" del usuario ahi es una columna; en el codigo se calcula.
       - El documento dice que la tabla usuarios tiene una columna "estado";
         el codigo tiene un booleano "activo".
     El LEEME.md ya documenta estas diferencias. Son decisiones conscientes.

  3. El documento academico dice que los reportes se exportan a PDF y Excel.
     La implementacion solo genera CSV.

  4. El documento academico dice que pagar la multa libera la sancion. En el
     codigo NO pasa: hay que levantarla a mano.

  5. (Corregido) El comentario de SecurityConfig sobre config/PasswordConfig.java
     (un archivo que no existe) se quito.

  6. Hay un archivo suelto en src/main/resources con un nombre GUID:
     {CD2B0866-316C-4AE4-B882-C35B04333196}.png. Parece un archivo subido por
     error y se esta empaquetando dentro del jar.

================================================================================
 FIN DEL DOCUMENTO
================================================================================