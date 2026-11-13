package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resultado de registrar una devolución.
 *
 * Si no hubo retraso ni cargo por daño/pérdida, id_multa y monto_multa vienen en null.
 * concepto_multa explica el motivo ("Retraso (3 días)", "Libro dañado"...).
 * monto_cobrado: lo que se cobró por el precio de la copia (null si la copia no tiene precio).
 * id_caja_movimiento: el movimiento de caja que dejó el cobro.
 */
public record DevolucionResponseDTO(
        Long id,
        @JsonProperty("id_prestamo") Long idPrestamo,
        @JsonProperty("fecha_devolucion") LocalDateTime fechaDevolucion,
        @JsonProperty("dias_retraso") int diasRetraso,
        String observaciones,
        @JsonProperty("id_multa") Long idMulta,
        @JsonProperty("monto_multa") BigDecimal montoMulta,
        /** Motivo de la multa generada (null si no hubo). */
        @JsonProperty("concepto_multa") String conceptoMulta,
        /** Si el ejemplar quedó apartado para la siguiente reserva de la cola. */
        @JsonProperty("id_reserva_notificada") Long idReservaNotificada,
        /** Precio de la copia cobrado al devolver (null si la copia no tiene precio). */
        @JsonProperty("monto_cobrado") BigDecimal montoCobrado,
        @JsonProperty("metodo_pago_cobro") String metodoPagoCobro,
        @JsonProperty("id_caja_movimiento") Long idCajaMovimiento
) {}
