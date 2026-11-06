package com.biblioteca.dto;

import com.biblioteca.entity.EstadoEjemplar;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RF14 - Body de PUT /api/ejemplares/{id}. Todos los campos opcionales.
 * estado permitido: DISPONIBLE, DANADO, PERDIDO o BAJA (PRESTADO y RESERVADO los pone el sistema).
 * precio: el precio de esta copia. Si se manda null, el precio NO cambia (el campo
 * se queda como estaba). Para que la copia vuelva a valer lo mismo que el libro,
 * manda explicitamente el precio general del libro.
 */
public record ActualizarEjemplarDTO(
        @Size(max = 100) String ubicacion,
        EstadoEjemplar estado,
        @JsonProperty("precio") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo") BigDecimal precio
) {}
