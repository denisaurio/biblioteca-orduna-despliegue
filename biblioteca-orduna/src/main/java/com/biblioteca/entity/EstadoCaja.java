package com.biblioteca.entity;

/** Estado de una caja (el cajón de la biblioteca). */
public enum EstadoCaja {
    /** Abierta: el personal está cobrando. Solo puede haber una a la vez. */
    ABIERTA,
    /** Cerrada: se hizo el arqueo y ya no admite cobros. */
    CERRADA
}
