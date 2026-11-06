package com.biblioteca.dto;

import com.biblioteca.entity.TipoAccesoVirtual;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cambio de una orden de libro virtual en el panel del personal.
 * Sirve para "cambiar orden": pasar de alquiler a compra, cambiar los dias, etc.
 * Lo que no se cambia aqui es el libro ni el lector.
 */
public record CambioAccesoVirtualDTO(
        TipoAccesoVirtual tipo,
        @Min(value = 1, message = "El alquiler es de al menos 1 día") @Max(value = 365, message = "El alquiler es de hasta 365 días")
        @JsonProperty("dias_alquiler") Integer diasAlquiler
) {}
