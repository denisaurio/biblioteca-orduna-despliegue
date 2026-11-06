package com.biblioteca.dto;

import com.biblioteca.entity.EstadoEjemplar;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Un ejemplar nuevo, dentro del registro de un libro.
 * Los ejemplares se agrupan bajo el libro: solo ellos se reservan.
 *
 * precio: precio de ESTA copia. Si no se manda, se usa el precio de compra del libro.
 */
public record EjemplarNuevoDTO(
        @JsonProperty("codigo_inventario") @NotBlank @Size(max = 50) String codigoInventario,
        @Size(max = 100) String ubicacion,
        @JsonProperty("estado") EstadoEjemplar estado,
        @JsonProperty("precio") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo") BigDecimal precio
) {}
