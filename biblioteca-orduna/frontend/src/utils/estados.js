// Texto y color de cada estado que devuelve el backend, para usar con <Badge color=...>.

export const estadoLibro = {
  DISPONIBLE: ['green', 'Disponible'],
  PRESTADO: ['red', 'Préstamo'],
  SIN_EJEMPLARES: ['amber', 'Sin ejemplares'],
};

export const estadoEjemplar = {
  DISPONIBLE: ['green', 'Disponible'],
  PRESTADO: ['red', 'Prestado'],
  RESERVADO: ['amber', 'Reservado'],
  DANADO: ['amber', 'Dañado'],
  PERDIDO: ['red', 'Perdido'],
  BAJA: ['blue', 'De baja'],
};

export const estadoUsuario = {
  ACTIVO: ['green', 'Activo'],
  INACTIVO: ['amber', 'Inactivo'],
  SUSPENDIDO: ['red', 'Suspendido'],
};

export const estadoPrestamo = {
  ACTIVO: ['blue', 'En plazo'],
  VENCIDO: ['red', 'Retraso'],
  DEVUELTO: ['green', 'Devuelto'],
};

export const estadoReserva = {
  PENDIENTE: ['amber', 'En espera'],
  NOTIFICADA: ['blue', 'Por recoger'],
  ATENDIDA: ['green', 'Atendida'],
  CANCELADA: ['red', 'Cancelada'],
  VENCIDA: ['red', 'Vencida'],
};

export const estadoMulta = {
  PENDIENTE: ['red', 'Pendiente'],
  PARCIAL: ['amber', 'Pago parcial'],
  PAGADA: ['green', 'Pagado'],
};

export const estadoSancion = {
  ACTIVA: ['red', 'Activa'],
  FINALIZADA: ['green', 'Finalizada'],
};

/** Tipo de libro: físico (se presta), virtual (PDF) o híbrido (las dos). */
export const tipoLibro = {
  FISICO: ['blue', 'Físico'],
  VIRTUAL: ['amber', 'Virtual'],
  HIBRIDO: ['green', 'Híbrido'],
};

/** Cómo se paga el acceso a un libro virtual. */
export const tipoAccesoVirtual = {
  ALQUILER: ['blue', 'Alquiler'],
  COMPRA: ['green', 'Compra'],
};

/** Estado de un pedido de libro virtual. */
export const estadoAccesoVirtual = {
  PENDIENTE_PAGO: ['amber', 'Pendiente de pago'],
  PAGADO: ['green', 'Pagado'],
  CANCELADO: ['red', 'Cancelado'],
};

/** Formas de pago de un libro virtual. (Yape por QR o efectivo en la tienda.) */
export const metodoPagoVirtual = {
  YAPE: 'Yape',
  EFECTIVO: 'Efectivo (en tienda)',
};

/** Estado del comprobante de Yape que sube el lector. */
export const estadoComprobante = {
  PENDIENTE: ['amber', 'En revisión'],
  APROBADO: ['green', 'Aprobado'],
  DENEGADO: ['red', 'Denegado'],
};

/** Estado de la caja (el cajón de dinero). */
export const estadoCaja = {
  ABIERTA: ['green', 'Abierta'],
  CERRADA: ['blue', 'Cerrada'],
};

/** Cada tipo de movimiento de dinero de la caja. */
export const tipoMovimientoCaja = {
  COBRO_PRESTAMO_FECHA_FIJA: ['blue', 'Préstamo (fecha fija)'],
  COBRO_PRESTAMO_ABIERTO: ['blue', 'Préstamo (abierto)'],
  COBRO_MULTA: ['red', 'Multa'],
  VENTA_VIRTUAL: ['amber', 'Venta de libro virtual'],
  ALQUILER_VIRTUAL: ['amber', 'Alquiler de libro virtual'],
};

export const tipoAviso = {
  VENCIMIENTO_PROXIMO: ['⏰', 'Vencimiento de préstamo'],
  PRESTAMO_VENCIDO: ['⚠️', 'Préstamo vencido'],
  RESERVA_DISPONIBLE: ['📘', 'Reserva disponible'],
  RESERVA_VENCIDA: ['⌛', 'Reserva vencida'],
  MULTA_PENDIENTE: ['💰', 'Multa generada'],
  NUEVO_LIBRO: ['✨', 'Nuevo libro en catálogo'],
  ACCESO_VIRTUAL: ['📖', 'Libro virtual listo'],
  COMPROBANTE_YAPE: ['🧾', 'Comprobante de Yape por revisar'],
  PAGO_RECHAZADO: ['❌', 'Comprobante denegado'],
  ADMINISTRATIVO: ['📢', 'Aviso de la biblioteca'],
};

/** Cada acción que queda auditada (ver TipoAccionAuditoria en el backend). */
export const tipoAccionAuditoria = {
  REGISTRAR_LIBRO: ['green', 'Registró un libro'],
  EDITAR_LIBRO: ['blue', 'Editó un libro'],
  ESCANEAR_ISBN: ['blue', 'Escaneó un ISBN'],
  SUBIR_PORTADA: ['blue', 'Subió una portada'],
  SUBIR_PDF: ['blue', 'Subió un PDF'],
  REGISTRAR_EJEMPLAR: ['green', 'Registró un ejemplar'],
  EDITAR_EJEMPLAR: ['blue', 'Editó un ejemplar'],
  REGISTRAR_PRESTAMO: ['green', 'Registró un préstamo'],
  REGISTRAR_DEVOLUCION: ['green', 'Registró una devolución'],
  COBRAR_MULTA: ['amber', 'Cobró una multa'],
  ABRIR_CAJA: ['green', 'Abrió la caja'],
  CERRAR_CAJA: ['amber', 'Cerró la caja'],
  ABRIR_TURNO: ['green', 'Abrió un turno'],
  CERRAR_TURNO: ['amber', 'Cerró un turno'],
  ACEPTAR_COMPROBANTE: ['green', 'Aprobó un comprobante'],
  DENEGAR_COMPROBANTE: ['red', 'Denegó un comprobante'],
  COBRAR_PEDIDO_VIRTUAL: ['green', 'Cobró un pedido virtual'],
  REGISTRAR_USUARIO: ['green', 'Registró a un lector'],
  EDITAR_USUARIO: ['blue', 'Editó un usuario'],
  APLICAR_SANCION: ['red', 'Aplicó una sanción'],
  LEVANTAR_SANCION: ['amber', 'Levantó una sanción'],
  CREAR_AVISO: ['blue', 'Creó un aviso'],
  CAMBIAR_CONFIGURACION: ['amber', 'Cambió la configuración'],
  PREPARAR_ALMACENAMIENTO: ['blue', 'Preparó el almacenamiento'],
};

/** Etiqueta de la entidad sobre la que se hizo la acción (columna "entidad"). */
export const entidadAuditoria = {
  LIBRO: 'Libro', EJEMPLAR: 'Ejemplar', ISBN: 'ISBN', PRESTAMO: 'Préstamo',
  DEVOLUCION: 'Devolución', MULTA: 'Multa', CAJA: 'Caja', TURNO: 'Turno',
  COMPROBANTE: 'Comprobante', ACCESO_VIRTUAL: 'Libro virtual', USUARIO: 'Usuario',
  SANCION: 'Sanción', AVISO: 'Aviso', CONFIGURACION: 'Configuración',
  ALMACENAMIENTO: 'Almacenamiento',
};

export const metodosPago = ['EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'YAPE', 'PLIN', 'OTRO'];
export const nombreMetodo = {
  EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', TRANSFERENCIA: 'Transferencia', YAPE: 'Yape', PLIN: 'Plin', OTRO: 'Otro',
};
