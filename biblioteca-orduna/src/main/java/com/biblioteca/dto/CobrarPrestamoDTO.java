package com.biblioteca.dto;

import com.biblioteca.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** POST /api/prestamos/{id}/cobrar - el encargado cobra un préstamo POR_COBRAR. */
public record CobrarPrestamoDTO(
        @NotNull(message = "Elige el método de pago")
        @JsonProperty("metodo_pago") MetodoPago metodoPago
) {}
