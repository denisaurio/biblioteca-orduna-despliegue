package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoMultaResponseDTO(
        Long id,
        @JsonProperty("id_multa") Long idMulta,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("monto_pagado") BigDecimal montoPagado,
        /** Efectivo realmente recibido. En efectivo es menor que el monto salda, por el descuento. */
        @JsonProperty("descuento") BigDecimal descuento,
        @JsonProperty("metodo_pago") String metodoPago,
        @JsonProperty("fecha_pago") LocalDateTime fechaPago,
        @JsonProperty("id_bibliotecario") Long idBibliotecario,
        /** Lo que falta pagar de la multa después de este pago. */
        @JsonProperty("saldo_pendiente") BigDecimal saldoPendiente,
        /** Estado de la multa después del pago: PARCIAL o PAGADA. */
        @JsonProperty("estado_multa") String estadoMulta
) {}
