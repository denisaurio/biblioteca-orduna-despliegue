package com.biblioteca.entity;

/**
 * Estado de una orden de acceso a un libro virtual.
 *
 * A diferencia del estado del libro, del usuario o del prestamo (que se calculan),
 * este SI se guarda: es el ciclo de vida de un pedido de pago, no un dato derivado.
 * Lo que NO se guarda es si el alquiler ya vencio: eso sale de la fecha_fin.
 */
public enum EstadoAccesoVirtual {
    /** El lector pidio el libro pero todavia no pago. */
    PENDIENTE_PAGO,
    /** Pago confirmado: el lector ya tiene el acceso. */
    PAGADO,
    /** El lector lo cancelo o el personal lo anulo. */
    CANCELADO
}
