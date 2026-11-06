package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** POST /api/cajas - abrir el cajón. */
public record AbrirCajaDTO(
        /** Dinero que ya había en el cajón al abrirlo. */
        @NotNull(message = "Escribe cuánto dinero hay en el cajón")
        @DecimalMin(value = "0.00", message = "El monto inicial no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El monto inicial admite dos decimales")
        @JsonProperty("monto_inicial") BigDecimal montoInicial,
        @Size(max = 300, message = "La observación no debe superar los 300 caracteres")
        String observaciones
) {}
