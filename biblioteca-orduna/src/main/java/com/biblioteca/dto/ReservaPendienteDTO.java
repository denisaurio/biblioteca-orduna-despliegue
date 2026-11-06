package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una reserva que todavía no se atendió.
 *
 * Por recoger: ya tiene un ejemplar apartado (NOTIFICADA) y hay que recogerlo antes
 * de la fecha límite. En cola: todavía espera turno.
 */
public record ReservaPendienteDTO(
        @JsonProperty("id_reserva") Long idReserva,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("titulo") String titulo,
        @JsonProperty("fecha_reserva") LocalDateTime fechaReserva,
        /** Solo en las que ya tienen ejemplar apartado. */
        @JsonProperty("fecha_limite_retiro") LocalDate fechaLimiteRetiro,
        @JsonProperty("dias_para_recoger") Long diasParaRecoger
) {}
