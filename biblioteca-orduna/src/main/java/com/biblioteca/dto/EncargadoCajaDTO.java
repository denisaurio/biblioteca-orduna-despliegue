package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Un tramo de encargado de caja: quién cobró desde/hasta qué hora (hasta = null: sigue). */
public record EncargadoCajaDTO(
        @JsonProperty("id_usuario") Long idUsuario,
        String nombre,
        LocalDateTime desde,
        LocalDateTime hasta,
        @JsonProperty("asignado_por") String asignadoPor
) {}
