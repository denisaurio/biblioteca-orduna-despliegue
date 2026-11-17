package com.biblioteca.dto;

import com.biblioteca.entity.TipoPrestamo;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * RF06 - Body de POST /api/prestamos (paso "Confirmar" del asistente).
 * Enviar id_ejemplar O id_libro: con id_libro el sistema elige el ejemplar disponible.
 * tipo_prestamo: FECHA_FIJA (fijo, por defecto: vence en los dias_max de la configuración)
 *                o PERSONALIZADO (vence en dias_prestamo, que es obligatorio).
 * tarifa_multa_diaria: opcional. Si viene, es una multa personalizada por día de retraso;
 *                si no, se copia la tarifa vigente de la configuración.
 */
public record NuevoPrestamoDTO(
        @JsonProperty("id_ejemplar") Long idEjemplar,
        @JsonProperty("id_libro") Long idLibro,
        @JsonProperty("id_usuario_lector") @NotNull Long idUsuarioLector,
        @JsonProperty("tipo_prestamo") TipoPrestamo tipoPrestamo,
        @JsonProperty("dias_prestamo") @Min(1) @Max(365) Integer diasPrestamo,
        @JsonProperty("tarifa_multa_diaria") @DecimalMin("0.00") @DecimalMax("9999.99") BigDecimal tarifaMultaDiaria
) {}
