package com.biblioteca.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** PUT /api/configuracion-caja - reemplaza TODOS los horarios por los que llegan. */
public record GuardarHorariosCajaDTO(
        @NotNull(message = "Envía la lista de horarios")
        @Valid List<HorarioCajaDTO> horarios
) {}
