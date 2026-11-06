package com.biblioteca.entity;

public enum TipoNotificacion {
    /** El préstamo vence mañana. */
    VENCIMIENTO_PROXIMO,
    /** El préstamo ya venció y no se ha devuelto. */
    PRESTAMO_VENCIDO,
    /** Llegó el turno del lector en la cola: hay un ejemplar apartado para él. */
    RESERVA_DISPONIBLE,
    /** No recogió el ejemplar apartado a tiempo. */
    RESERVA_VENCIDA,
    /** El lector tiene una multa sin pagar. */
    MULTA_PENDIENTE,
    /** Se agregó un libro al catálogo. */
    NUEVO_LIBRO,
    /** El libro virtual se pagó y el lector ya lo tiene. */
    ACCESO_VIRTUAL,
    /** El lector subio su comprobante de Yape: el personal tiene que revisarlo. */
    COMPROBANTE_YAPE,
    /** El personal rechazo el comprobante de Yape del lector. */
    PAGO_RECHAZADO,
    /** Aviso creado a mano por un bibliotecario o gerente ("+ Crear aviso"). */
    ADMINISTRATIVO
}
