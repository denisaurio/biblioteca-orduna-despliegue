package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un préstamo que sigue fuera de la biblioteca (falta devolver). */
public record PrestamoPendienteDTO(
        @JsonProperty("id_prestamo") Long idPrestamo,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("titulo") String titulo,
        String tipo,
        @JsonProperty("fecha_prestamo") LocalDateTime fechaPrestamo,
        /** null en los préstamos abiertos: no tienen fecha de vencimiento. */
        @JsonProperty("fecha_vencimiento") LocalDate fechaVencimiento,
        /** Lo que se cobra al devolverlo (precio de la copia). null si no tiene precio. */
        @JsonProperty("precio_a_cobrar") BigDecimal precioACobrar,
        @JsonProperty("dias_de_retraso") long diasDeRetraso
) {}
