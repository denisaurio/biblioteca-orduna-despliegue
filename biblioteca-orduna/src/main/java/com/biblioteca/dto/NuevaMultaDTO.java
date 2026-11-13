package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RF15 - Body de POST /api/multas ("+ Registrar multa").
 * Sirve para generar una multa sobre una devolución que ya existe y que quedó sin multa
 * (por ejemplo, libros dañados devueltos antes de que existiera el cargo por daño).
 * El bibliotecario que la genera sale del JWT.
 */
public record NuevaMultaDTO(
        @JsonProperty("id_devolucion") @NotNull Long idDevolucion,
        @NotNull @DecimalMin("0.01") BigDecimal monto,
        @Size(max = 120) String concepto
) {}
