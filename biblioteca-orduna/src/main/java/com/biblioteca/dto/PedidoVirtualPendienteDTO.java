package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un pedido de libro virtual que todavía no se cobró. */
public record PedidoVirtualPendienteDTO(
        Long id,
        @JsonProperty("nombre_usuario") String nombreUsuario,
        @JsonProperty("titulo") String titulo,
        String tipo,
        @JsonProperty("metodo_pago") String metodoPago,
        BigDecimal precio,
        @JsonProperty("fecha_creacion") LocalDateTime fechaCreacion,
        /** Estado del comprobante de Yape, si el cliente ya lo subió. */
        @JsonProperty("estado_comprobante") String estadoComprobante
) {}
