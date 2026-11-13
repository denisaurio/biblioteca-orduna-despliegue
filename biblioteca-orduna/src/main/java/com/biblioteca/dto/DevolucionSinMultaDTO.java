package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Devolución ya registrada que todavía no tiene multa. La usa "Pagos de multas"
 * en el selector de "+ Registrar multa". Incluye la tarifa copiada en el préstamo
 * para que el frontend pueda sugerir el monto por días de retraso.
 */
public record DevolucionSinMultaDTO(
        @JsonProperty("id_devolucion") Long idDevolucion,
        @JsonProperty("id_prestamo") Long idPrestamo,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("dni_lector") String dniLector,
        @JsonProperty("titulo_libro") String tituloLibro,
        @JsonProperty("codigo_inventario") String codigoInventario,
        @JsonProperty("fecha_devolucion") LocalDateTime fechaDevolucion,
        @JsonProperty("dias_retraso") int diasRetraso,
        @JsonProperty("tarifa_multa_diaria") BigDecimal tarifaMultaDiaria,
        @JsonProperty("precio_copia") BigDecimal precioCopia
) {}
