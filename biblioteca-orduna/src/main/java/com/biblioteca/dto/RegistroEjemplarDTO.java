package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RF14 - Body de POST /api/libros/{idLibro}/ejemplares
 * precio: precio de esta copia. Si no se manda, se usa el precio de compra del libro.
 */
public record RegistroEjemplarDTO(
        @JsonProperty("codigo_inventario") @NotBlank @Size(max = 50) String codigoInventario,
        @Size(max = 100) String ubicacion,
        @JsonProperty("precio") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo") BigDecimal precio
) {}
