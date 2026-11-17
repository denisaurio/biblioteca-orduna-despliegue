package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pantalla "Pagos de multa" y "Mis multas". estado: PENDIENTE, PARCIAL o PAGADA.
 * id_caja_prestamo / id_caja_devolucion: cajas donde empezó y terminó el préstamo.
 * cajas_pago: cajas donde se pagó la multa (varias si se pagó en partes).
 */
public record MultaResponseDTO(
        Long id,
        @JsonProperty("id_usuario") Long idUsuario,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("dni_lector") String dniLector,
        @JsonProperty("titulo_libro") String tituloLibro,
        @JsonProperty("dias_retraso") int diasRetraso,
        /** Motivo: "Retraso (3 días)", "Libro dañado", "Multa manual"... */
        String concepto,
        BigDecimal monto,
        @JsonProperty("monto_pagado") BigDecimal montoPagado,
        BigDecimal saldo,
        String estado,
        @JsonProperty("fecha_generacion") LocalDateTime fechaGeneracion,
        @JsonProperty("ultimo_metodo_pago") String ultimoMetodoPago,
        @JsonProperty("fecha_ultimo_pago") LocalDateTime fechaUltimoPago,
        @JsonProperty("id_caja_prestamo") Long idCajaPrestamo,
        @JsonProperty("id_caja_devolucion") Long idCajaDevolucion,
        @JsonProperty("cajas_pago") List<Long> cajasPago
) {}
