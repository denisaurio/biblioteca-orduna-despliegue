package com.biblioteca.dto;

import com.biblioteca.entity.EstadoEjemplar;
import com.biblioteca.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RF07/RF08 - Body de POST /api/devoluciones.
 * estado_ejemplar (opcional): DISPONIBLE (por defecto), DANADO, PERDIDO o BAJA, según cómo llegó el libro.
 *
 * metodo_pago (opcional): con qué se cobra el precio de la copia. Por defecto EFECTIVO.
 * El 15% de descuento es SOLO de las multas: aquí se cobra el precio completo.
 *
 * cargo_danado (opcional): monto que se suma a la multa cuando el libro vuelve dañado,
 * perdido o de baja. Lo decide el bibliotecario en el formulario (el frontend sugiere
 * 50% del precio de la copia si está dañado y 100% si está perdido). No es dinero que
 * se cobre en ese momento: es deuda que se paga después en "Pagos de multas".
 */
public record NuevaDevolucionDTO(
        @JsonProperty("id_prestamo") @NotNull Long idPrestamo,
        @Size(max = 500) String observaciones,
        @JsonProperty("estado_ejemplar") EstadoEjemplar estadoEjemplar,
        @JsonProperty("metodo_pago") MetodoPago metodoPago,
        @JsonProperty("cargo_danado") @DecimalMin("0.00") BigDecimal cargoDanado
) {}
