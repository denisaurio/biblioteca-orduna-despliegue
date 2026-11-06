package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un movimiento de dinero de la caja. */
public record CajaMovimientoDTO(
        Long id,
        @JsonProperty("id_caja") Long idCaja,
        String tipo,
        BigDecimal monto,
        BigDecimal descuento,
        @JsonProperty("metodo_pago") String metodoPago,
        String descripcion,
        LocalDateTime fecha,
        @JsonProperty("nombre_usuario") String nombreUsuario,
        @JsonProperty("id_prestamo") Long idPrestamo,
        @JsonProperty("id_pago_multa") Long idPagoMulta,
        @JsonProperty("id_devolucion") Long idDevolucion,
        @JsonProperty("id_acceso_virtual") Long idAccesoVirtual
) {}
