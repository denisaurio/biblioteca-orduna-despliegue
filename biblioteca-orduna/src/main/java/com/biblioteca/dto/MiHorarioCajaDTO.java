package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Lo que ve el personal en la pantalla Caja: todos sus horarios y, si hoy le toca,
 * el horario de hoy (para saber a qué hora se cierra la caja sola).
 */
public record MiHorarioCajaDTO(
        @JsonProperty("horario_hoy") HorarioCajaResponseDTO horarioHoy,
        List<HorarioCajaResponseDTO> horarios
) {}
