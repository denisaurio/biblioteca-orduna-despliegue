package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * estado (derivado de los ejemplares, no se edita):
 * DISPONIBLE (hay al menos uno), PRESTADO (todos prestados/reservados) o SIN_EJEMPLARES.
 *
 * tipo_libro: FISICO, VIRTUAL o HIBRIDO.
 * tiene_pdf: si el libro tiene PDF cargado. La clave del PDF NUNCA sale: el archivo
 * se entrega con una URL firmada que caduca.
 * aviso: solo al registrar/editar, si algo opcional no se pudo hacer (por ejemplo, bajar
 * la portada de internet). El libro SI se guardo; el aviso dice que falta.
 */
public record LibroResponseDTO(
        Long id,
        String titulo,
        String isbn,
        String editorial,
        @JsonProperty("anio_publicacion") Integer anioPublicacion,
        @JsonProperty("num_paginas") Integer numPaginas,
        List<AutorResponseDTO> autores,
        List<GeneroResponseDTO> generos,
        @JsonProperty("ejemplares_total") long ejemplaresTotal,
        @JsonProperty("ejemplares_disponibles") long ejemplaresDisponibles,
        String estado,
        @JsonProperty("tipo_libro") String tipoLibro,
        @JsonProperty("precio_alquiler_diario") BigDecimal precioAlquilerDiario,
        @JsonProperty("precio_compra") BigDecimal precioCompra,
        @JsonProperty("url_imagen") String urlImagen,
        @JsonProperty("tiene_pdf") boolean tienePdf,
        @JsonInclude(JsonInclude.Include.NON_NULL) String aviso
) {
    /** El mismo libro con un aviso para el bibliotecario. */
    public LibroResponseDTO conAviso(String texto) {
        return new LibroResponseDTO(id, titulo, isbn, editorial, anioPublicacion, numPaginas, autores, generos,
                ejemplaresTotal, ejemplaresDisponibles, estado, tipoLibro, precioAlquilerDiario, precioCompra,
                urlImagen, tienePdf, texto);
    }
}
