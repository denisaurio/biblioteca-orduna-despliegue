package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Nota interna sobre un cliente (Ficha 360). */
public record NotaClienteDTO(
        Long id,
        String texto,
        LocalDateTime fecha,
        @JsonProperty("nombre_autor") String nombreAutor
) {}
