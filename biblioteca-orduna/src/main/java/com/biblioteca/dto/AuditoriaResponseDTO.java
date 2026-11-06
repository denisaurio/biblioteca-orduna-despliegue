package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Una acción auditada. La ve solo el gerente.
 *
 * nombre_usuario y rol_usuario vienen del usuario que la hizo (null si la hizo alguien
 * sin sesión, por ejemplo un auto-registro).
 */
public record AuditoriaResponseDTO(
        Long id,
        LocalDateTime fecha,
        @JsonProperty("id_usuario") Long idUsuario,
        @JsonProperty("nombre_usuario") String nombreUsuario,
        @JsonProperty("rol_usuario") String rolUsuario,
        String accion,
        String entidad,
        @JsonProperty("entidad_id") Long entidadId,
        String descripcion
) {}
