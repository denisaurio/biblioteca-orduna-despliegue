package com.biblioteca.entity;

public enum EstadoComprobante {
    /** El cliente subio la captura: falta que el personal la mire. */
    PENDIENTE,
    /** El personal la acepto: el lector ya tiene el libro virtual. */
    APROBADO,
    /** El personal la rechazo: el lector puede subir otra. */
    DENEGADO
}
