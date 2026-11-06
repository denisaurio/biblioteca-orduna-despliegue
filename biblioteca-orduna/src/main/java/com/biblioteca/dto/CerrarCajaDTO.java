package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** POST /api/cajas/{id}/cerrar - cerrar el cajón con el efectivo contado. */
public record CerrarCajaDTO(
        /** Efectivo contado en el cajón al cerrar. */
        @NotNull(message = "Escribe cuánto efectivo hay en el cajón")
        @DecimalMin(value = "0.00", message = "El monto contado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El monto contado admite dos decimales")
        @JsonProperty("monto_final_declarado") BigDecimal montoFinalDeclarado,
        @Size(max = 300, message = "La observación no debe superar los 300 caracteres")
        String observaciones
) {}
