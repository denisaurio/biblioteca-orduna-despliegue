package com.biblioteca.dto;

import com.biblioteca.entity.EstadoEjemplar;
import com.biblioteca.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RF07/RF08 - Body de POST /api/devoluciones.
 * estado_ejemplar (opcional): DISPONIBLE (por defecto), DANADO o PERDIDO, según cómo llegó el libro.
 *
 * metodo_pago (opcional): con qué se cobra el precio de la copia. Por defecto EFECTIVO.
 * El 15% de descuento es SOLO de las multas: aquí se cobra el precio completo.
 */
public record NuevaDevolucionDTO(
        @JsonProperty("id_prestamo") @NotNull Long idPrestamo,
        @Size(max = 500) String observaciones,
        @JsonProperty("estado_ejemplar") EstadoEjemplar estadoEjemplar,
        @JsonProperty("metodo_pago") MetodoPago metodoPago
) {}
