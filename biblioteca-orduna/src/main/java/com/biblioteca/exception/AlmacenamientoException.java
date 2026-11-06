package com.biblioteca.exception;

/**
 * Fallo al guardar o leer un archivo en el almacenamiento externo (Cloudflare R2).
 * No es una regla de negocio incumplida sino una caida del servicio: responde HTTP 500.
 */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje) {
        super(mensaje);
    }

    public AlmacenamientoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
