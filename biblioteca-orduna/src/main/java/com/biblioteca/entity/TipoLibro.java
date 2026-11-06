package com.biblioteca.entity;

/**
 * Tipo de libro del catalogo:
 *   FISICO  -> se presta en la biblioteca, tiene ejemplares con codigo de inventario.
 *   VIRTUAL -> solo PDF (guardado en Cloudflare R2), se alquila o se compra.
 *   HIBRIDO -> tiene las dos cosas: ejemplares en estanteria y PDF.
 */
public enum TipoLibro {
    FISICO,
    VIRTUAL,
    HIBRIDO
}
