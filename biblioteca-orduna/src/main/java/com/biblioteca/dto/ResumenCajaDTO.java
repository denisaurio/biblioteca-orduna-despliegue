package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Lo que ve el personal en la pestaña "Hoy" de la caja.
 *
 * caja_abierta: la caja abierta ahora (null si no hay ninguna).
 * total_dia: todo lo cobrado HOY, de todas las cajas (es el movimiento, la única fuente).
 * total_caja: todo lo cobrado en la caja abierta.
 * por_metodo / por_tipo: el desglose de HOY.
 * movimientos: los movimientos de la caja abierta.
 */
public record ResumenCajaDTO(
        @JsonProperty("caja_abierta") CajaResponseDTO cajaAbierta,
        @JsonProperty("total_dia") BigDecimal totalDia,
        @JsonProperty("total_caja") BigDecimal totalCaja,
        @JsonProperty("por_metodo") Map<String, BigDecimal> porMetodo,
        @JsonProperty("por_tipo") Map<String, BigDecimal> porTipo,
        List<CajaMovimientoDTO> movimientos
) {}
