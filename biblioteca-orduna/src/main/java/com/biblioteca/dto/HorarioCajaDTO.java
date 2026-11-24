package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Una fila del horario de caja: día, horas y encargado. */
public record HorarioCajaDTO(
        /** MONDAY, TUESDAY... (el nombre del enum java.time.DayOfWeek). */
        @NotNull(message = "Elige el día de la semana")
        @JsonProperty("dia_semana") DayOfWeek diaSemana,
        @NotNull(message = "Indica la hora de inicio")
        @JsonProperty("hora_inicio") LocalTime horaInicio,
        @NotNull(message = "Indica la hora de fin")
        @JsonProperty("hora_fin") LocalTime horaFin,
        /** Null si ese día no tiene encargado asignado. */
        @JsonProperty("id_encargado") Long idEncargado
) {}
