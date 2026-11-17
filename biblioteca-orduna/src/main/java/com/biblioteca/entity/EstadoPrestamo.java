package com.biblioteca.entity;

/**
 * Estado de un préstamo.
 *
 * POR_COBRAR: lo registró un bibliotecario que no es el encargado de la caja. El
 *             ejemplar queda apartado (PRESTADO) pero el libro no sale hasta que el
 *             encargado (o el gerente) cobra el precio. Al cobrarlo pasa a ACTIVO.
 * ACTIVO:     el libro está fuera (VENCIDO es ACTIVO pasado de fecha, no un estado).
 * DEVUELTO:   el libro volvió.
 * ANULADO:    se registró pero el cliente no pagó: el ejemplar se liberó.
 *
 * La columna prestamos.estado es VARCHAR(20) (ver data.sql) para que entren
 * POR_COBRAR y ANULADO en bases creadas con la versión anterior.
 */
public enum EstadoPrestamo { ACTIVO, DEVUELTO, POR_COBRAR, ANULADO }
