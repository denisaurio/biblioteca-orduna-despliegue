package com.biblioteca.entity;

/**
 * Como se paga el acceso a un libro virtual:
 *   ALQUILER -> se lee en la aplicacion por un numero de dias ("1 sol el dia"), sin descargar.
 *   COMPRA   -> se paga una vez y el lector ya puede descargar el PDF.
 */
public enum TipoAccesoVirtual {
    ALQUILER,
    COMPRA
}
