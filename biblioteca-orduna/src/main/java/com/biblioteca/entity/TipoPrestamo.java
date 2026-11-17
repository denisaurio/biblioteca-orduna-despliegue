package com.biblioteca.entity;

/**
 * Regla de negocio: un préstamo es FIJO (FECHA_FIJA: vence en los "dias_max" de la
 * configuración) o PERSONALIZADO (el personal elige cuántos días). Los dos tienen fecha de
 * devolución y los dos generan multa por retraso: días de retraso × tarifa copiada en el préstamo.
 *
 * ABIERTO ya no se puede crear: queda solo para leer los préstamos antiguos que lo usaban
 * (no tienen fecha ni multa). No lo borres o esas filas dejarían de cargar.
 */
public enum TipoPrestamo {
    FECHA_FIJA,
    PERSONALIZADO,
    /** Solo préstamos antiguos. No se crean préstamos nuevos de este tipo. */
    ABIERTO
}
