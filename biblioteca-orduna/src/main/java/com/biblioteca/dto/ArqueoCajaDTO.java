package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** POST /api/cajas/{id}/arqueo - el encargado declara el efectivo que contó en el cajón. */
public record ArqueoCajaDTO(
        @NotNull(message = "Escribe cuánto efectivo contaste en el cajón")
        @DecimalMin(value = "0.00", message = "El monto contado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El monto contado admite dos decimales")
        @JsonProperty("monto_contado") BigDecimal montoContado,
        @Size(max = 300, message = "La observación no debe superar los 300 caracteres")
        String observaciones
) {}
