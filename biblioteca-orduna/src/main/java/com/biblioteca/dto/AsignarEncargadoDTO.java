package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** PUT /api/cajas/{id}/encargado - el gerente cambia el encargado sin cerrar la caja. */
public record AsignarEncargadoDTO(
        @NotNull(message = "Elige al nuevo encargado")
        @JsonProperty("id_encargado") Long idEncargado
) {}
