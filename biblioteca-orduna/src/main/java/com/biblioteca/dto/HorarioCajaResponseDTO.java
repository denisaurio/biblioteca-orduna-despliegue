package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

/** Un horario de caja tal como lo ve el frontend. */
public record HorarioCajaResponseDTO(
        Long id,
        @JsonProperty("dia_semana") String diaSemana,
        @JsonProperty("hora_inicio") @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonProperty("hora_fin") @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        @JsonProperty("id_encargado") Long idEncargado,
        @JsonProperty("nombre_encargado") String nombreEncargado
) {}
