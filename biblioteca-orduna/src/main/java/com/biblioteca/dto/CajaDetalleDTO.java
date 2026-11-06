package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Una caja con sus movimientos (detalle e historial). */
public record CajaDetalleDTO(
        @JsonProperty("caja") CajaResponseDTO caja,
        List<CajaMovimientoDTO> movimientos
) {}
