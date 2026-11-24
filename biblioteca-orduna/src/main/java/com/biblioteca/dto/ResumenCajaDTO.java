package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Lo que ve el personal en la pestaña "Hoy" de la caja.
 *
 * caja_abierta: la caja abierta ahora (null si no hay ninguna).
 * horario_hoy: horario configurado para hoy (inicio, fin y encargado), para la cuenta regresiva.
 * caja_cerrada_hoy: ya hubo una caja cerrada hoy; la apertura automática no abre otra.
 * total_dia: todo lo cobrado HOY, de todas las cajas (es el movimiento, la única fuente).
 * total_caja: todo lo cobrado en la caja abierta.
 * por_metodo / por_tipo: el desglose de HOY.
 * movimientos: los movimientos de la caja abierta.
 * puedo_cobrar: true si el usuario logueado es el encargado de la caja abierta o el gerente.
 * por_cobrar: préstamos registrados por otro bibliotecario que el encargado debe cobrar.
 */
public record ResumenCajaDTO(
        @JsonProperty("caja_abierta") CajaResponseDTO cajaAbierta,
        @JsonProperty("horario_hoy") HorarioCajaResponseDTO horarioHoy,
        @JsonProperty("caja_cerrada_hoy") boolean cajaCerradaHoy,
        @JsonProperty("total_dia") BigDecimal totalDia,
        @JsonProperty("total_caja") BigDecimal totalCaja,
        @JsonProperty("por_metodo") Map<String, BigDecimal> porMetodo,
        @JsonProperty("por_tipo") Map<String, BigDecimal> porTipo,
        List<CajaMovimientoDTO> movimientos,
        @JsonProperty("puedo_cobrar") boolean puedoCobrar,
        @JsonProperty("por_cobrar") List<PrestamoResponseDTO> porCobrar
) {}
