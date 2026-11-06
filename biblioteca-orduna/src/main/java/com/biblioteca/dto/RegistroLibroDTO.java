package com.biblioteca.dto;

import com.biblioteca.entity.TipoLibro;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * RF01 - Body de POST /api/libros y PUT /api/libros/{id}.
 * Según el documento: título, autor, ISBN y género son obligatorios.
 *
 * tipo_libro: FISICO (prestamo en biblioteca), VIRTUAL (PDF en R2) o HIBRIDO (las dos).
 * Si no se manda, se asume FISICO.
 */
public record RegistroLibroDTO(
        @NotBlank @Size(max = 200) String titulo,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^(?:\\d{9}[0-9Xx]|\\d{13})$",
                 message = "El ISBN debe tener 10 o 13 dígitos (el décimo puede ser X)")
        String isbn,
        @Size(max = 120) String editorial,
        @JsonProperty("anio_publicacion") Integer anioPublicacion,
        @JsonProperty("num_paginas") @Min(1) Integer numPaginas,
        /** ids de autores (tabla libros_autores). Al menos uno. */
        @JsonProperty("ids_autores") @NotEmpty(message = "Debe indicar al menos un autor") List<Long> idsAutores,
        /** ids de géneros (tabla libros_generos). Al menos uno. */
        @JsonProperty("ids_generos") @NotEmpty(message = "Debe indicar al menos un género") List<Long> idsGeneros,
        @JsonProperty("tipo_libro") TipoLibro tipoLibro,
        /** Precio por dia de alquiler del PDF. Solo para VIRTUAL e HIBRIDO. */
        @JsonProperty("precio_alquiler_diario") @DecimalMin(value = "0.00", message = "El precio de alquiler no puede ser negativo")
        BigDecimal precioAlquilerDiario,
        /** Precio de compra. Del PDF si el libro es virtual, del ejemplar si es fisico. */
        @JsonProperty("precio_compra") @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo")
        BigDecimal precioCompra
) {
    /** Tipo con el que se guarda: si no se indico, es un libro fisico. */
    public TipoLibro tipoEfectivo() {
        return tipoLibro == null ? TipoLibro.FISICO : tipoLibro;
    }
}
