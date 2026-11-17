package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Una caja con sus movimientos (detalle e historial).
 * prestamos_iniciados: préstamos que empezaron (se cobraron) en esta caja.
 * prestamos_finalizados: préstamos que se devolvieron en esta caja (pueden haber empezado en otra).
 */
public record CajaDetalleDTO(
        @JsonProperty("caja") CajaResponseDTO caja,
        List<CajaMovimientoDTO> movimientos,
        @JsonProperty("prestamos_iniciados") List<PrestamoResponseDTO> prestamosIniciados,
        @JsonProperty("prestamos_finalizados") List<PrestamoResponseDTO> prestamosFinalizados
) {}
