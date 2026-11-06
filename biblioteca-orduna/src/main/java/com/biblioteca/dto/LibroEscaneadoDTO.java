package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resultado de buscar un ISBN en internet (Google Books y Open Library a la vez).
 *
 * El bibliotecario escanea el codigo de barras del libro: el backend busca los datos,
 * los cruza con la biblioteca y el formulario se rellena solo.
 *
 * Autores y generos llegan de dos formas:
 *   - *_existentes: los que YA estan en la biblioteca (con su id): se marcan solos.
 *   - *_nuevos: los que no existen todavia. El formulario los marca y, al guardar,
 *     el backend los crea (RegistroLibroCompletoDTO.autores_nuevos / generos_nuevos).
 *
 * id_libro_existente: si ese ISBN ya esta registrado, el id del libro (para ir a
 * agregarle copias en vez de registrarlo dos veces).
 */
public record LibroEscaneadoDTO(
        String isbn,
        String titulo,
        /** Todos los autores encontrados, por nombre. */
        List<String> autores,
        String editorial,
        @JsonProperty("anio_publicacion") Integer anioPublicacion,
        @JsonProperty("num_paginas") Integer numPaginas,
        /** Portada encontrada en internet. El backend la baja y la guarda en R2 al crear el libro. */
        @JsonProperty("url_portada") String urlPortada,
        /** De donde salio la informacion: GOOGLE_BOOKS, OPEN_LIBRARY o las dos. */
        String fuente,
        @JsonProperty("autores_existentes") List<AutorResponseDTO> autoresExistentes,
        @JsonProperty("autores_nuevos") List<String> autoresNuevos,
        @JsonProperty("generos_existentes") List<GeneroResponseDTO> generosExistentes,
        @JsonProperty("generos_nuevos") List<String> generosNuevos,
        @JsonProperty("id_libro_existente") Long idLibroExistente,
        @JsonProperty("titulo_libro_existente") String tituloLibroExistente
) {}
