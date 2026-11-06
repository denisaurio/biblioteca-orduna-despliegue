package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comprobante de un pago con Yape.
 *
 * El personal ve el estado (PENDIENTE, APROBADO, DENEGADO), el monto y quien lo
 * subio. La imagen NO viaja aqui: se pide aparte con una URL firmada
 * (GET /api/comprobantes/{id}/imagen).
 */
public record ComprobantePagoResponseDTO(
        Long id,
        @JsonProperty("id_acceso_virtual") Long idAccesoVirtual,
        String titulo,
        @JsonProperty("id_usuario") Long idUsuario,
        @JsonProperty("nombre_usuario") String nombreUsuario,
        @JsonProperty("email_usuario") String emailUsuario,
        String tipo,
        BigDecimal monto,
        @JsonProperty("referencia_yape") String referenciaYape,
        @JsonProperty("fecha_envio") LocalDateTime fechaEnvio,
        String estado,
        @JsonProperty("id_revisado_por") Long idRevisadoPor,
        @JsonProperty("nombre_revisor") String nombreRevisor,
        @JsonProperty("fecha_revision") LocalDateTime fechaRevision,
        /** Por que se denego (lo ve el lector). */
        String motivo
) {}
