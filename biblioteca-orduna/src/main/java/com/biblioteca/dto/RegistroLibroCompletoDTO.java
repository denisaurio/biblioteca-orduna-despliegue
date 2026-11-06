package com.biblioteca.dto;

import com.biblioteca.entity.TipoLibro;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Registro completo de un libro: sus datos, su tipo y sus precios.
 *
 * Va en la parte "libro" de una peticion multipart (POST /api/libros/completo).
 * A diferencia de POST /api/libros, aqui si se puede adjuntar la portada y el PDF
 * en el mismo envio, porque el archivo va aparte en el formulario.
 *
 * Autores y generos: ids de los que ya existen (ids_autores, ids_generos) y, ademas,
 * NOMBRES de los que todavia no existen (autores_nuevos, generos_nuevos), que son los
 * que trajo el escaner de ISBN. El servicio crea los nuevos (o reutiliza el que ya
 * exista con ese nombre). Entre las dos listas tiene que haber al menos un autor y un
 * genero: eso lo valida LibroService, porque aqui ninguna de las dos es obligatoria sola.
 *
 * ejemplares: las copias del libro. El formulario de alta NO las envia (las copias
 * se dan de alta en el modulo Ejemplares), asi que normalmente llega vacio.
 */
public record RegistroLibroCompletoDTO(
        @NotBlank @Size(max = 200) String titulo,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^(?:\\d{9}[0-9Xx]|\\d{13})$",
                 message = "El ISBN debe tener 10 o 13 dígitos (el décimo puede ser X)")
        String isbn,
        @Size(max = 120) String editorial,
        @JsonProperty("anio_publicacion") Integer anioPublicacion,
        @JsonProperty("num_paginas") @Min(1) Integer numPaginas,
        @JsonProperty("ids_autores") List<Long> idsAutores,
        @JsonProperty("ids_generos") List<Long> idsGeneros,
        @JsonProperty("autores_nuevos") @Size(max = 10, message = "Como maximo 10 autores nuevos")
        List<@NotBlank @Size(max = 150) String> autoresNuevos,
        @JsonProperty("generos_nuevos") @Size(max = 10, message = "Como maximo 10 generos nuevos")
        List<@NotBlank @Size(max = 80) String> generosNuevos,
        @JsonProperty("tipo_libro") TipoLibro tipoLibro,
        @JsonProperty("precio_alquiler_diario") @DecimalMin(value = "0.00", message = "El precio de alquiler no puede ser negativo")
        BigDecimal precioAlquilerDiario,
        @JsonProperty("precio_compra") @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo")
        BigDecimal precioCompra,
        /** Ejemplares del libro, agrupados bajo el. Lo normal es que llegue vacio. */
        @JsonProperty("ejemplares") @Valid List<EjemplarNuevoDTO> ejemplares,
        /**
         * URL de una portada ya existente en internet (la que salio al escanear el ISBN).
         * El backend la baja y la guarda en Cloudflare R2. Solo se aceptan algunos sitios.
         */
        @JsonProperty("url_portada") String urlPortada,
        /** true: quitar la portada que tiene el libro (solo en la edicion). */
        @JsonProperty("quitar_portada") Boolean quitarPortada
) {
    public TipoLibro tipoEfectivo() {
        return tipoLibro == null ? TipoLibro.FISICO : tipoLibro;
    }

    public List<String> autoresNuevosOVacio() {
        return autoresNuevos == null ? List.of() : autoresNuevos;
    }

    public List<String> generosNuevosOVacio() {
        return generosNuevos == null ? List.of() : generosNuevos;
    }

    public boolean quitarPortadaPedido() {
        return Boolean.TRUE.equals(quitarPortada);
    }

    /**
     * Los mismos datos de libro, en el formato del registro simple, con los ids ya
     * resueltos (los que venian mas los de los autores/generos recien creados).
     */
    public RegistroLibroDTO aRegistroLibro(List<Long> idsAutoresFinal, List<Long> idsGenerosFinal) {
        return new RegistroLibroDTO(titulo, isbn, editorial, anioPublicacion, numPaginas,
                idsAutoresFinal, idsGenerosFinal, tipoLibro, precioAlquilerDiario, precioCompra);
    }
}
