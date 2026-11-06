package com.biblioteca.dto;

import com.biblioteca.entity.MetodoPago;
import com.biblioteca.entity.TipoAccesoVirtual;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Pedido de acceso a un libro virtual (POST /api/accesos-virtuales).
 *
 * tipo: ALQUILER (dias_alquiler obligatorio) o COMPRA.
 * metodo_pago: YAPE (QR estatico + comprobante que aprueba el personal) o
 * EFECTIVO (pagar en la tienda con el codigo de 24 horas).
 */
public record RegistroAccesoVirtualDTO(
        @NotNull(message = "Debe indicar el libro") @JsonProperty("id_libro") Long idLibro,
        @NotNull(message = "Debe indicar si es alquiler o compra") TipoAccesoVirtual tipo,
        @Min(value = 1, message = "El alquiler es de al menos 1 día") @Max(value = 365, message = "El alquiler es de hasta 365 días")
        @JsonProperty("dias_alquiler") Integer diasAlquiler,
        @NotNull(message = "Debe indicar cómo va a pagar") @JsonProperty("metodo_pago") MetodoPago metodoPago
) {}
