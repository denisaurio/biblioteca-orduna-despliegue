package com.biblioteca.entity;

/**
 * Por qué entró dinero a la caja.
 *
 * Cada movimiento apunta al préstamo, al pago de multa o al pedido virtual que lo
 * originó, así que el mismo cobro no se puede registrar dos veces (la base de datos
 * lo impide con índices únicos).
 */
public enum TipoMovimientoCaja {
    /** Se cobra el precio de la copia al devolver un préstamo de fecha fija. */
    COBRO_PRESTAMO_FECHA_FIJA,
    /** Se cobra el precio de la copia al devolver un préstamo abierto. */
    COBRO_PRESTAMO_ABIERTO,
    /** Se cobra una multa (el 15% de descuento en efectivo queda en el movimiento). */
    COBRO_MULTA,
    /** Se cobra una compra de libro virtual. */
    VENTA_VIRTUAL,
    /** Se cobra un alquiler de libro virtual. */
    ALQUILER_VIRTUAL
}
