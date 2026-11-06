package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * estado: ACTIVO, VENCIDO (activo y pasado de fecha) o DEVUELTO.
 * en_plazo: false cuando está vencido ("Retraso" en la pantalla Devoluciones).
 * precio_copia: precio del ejemplar cuando se prestó (copiado, no se recalcula).
 */
public record PrestamoResponseDTO(
        Long id,
        @JsonProperty("id_ejemplar") Long idEjemplar,
        @JsonProperty("codigo_inventario") String codigoInventario,
        @JsonProperty("id_libro") Long idLibro,
        @JsonProperty("titulo_libro") String tituloLibro,
        @JsonProperty("id_usuario_lector") Long idUsuarioLector,
        @JsonProperty("nombre_lector") String nombreLector,
        @JsonProperty("dni_lector") String dniLector,
        @JsonProperty("id_bibliotecario") Long idBibliotecario,
        @JsonProperty("tipo_prestamo") String tipoPrestamo,
        @JsonProperty("fecha_prestamo") LocalDateTime fechaPrestamo,
        @JsonProperty("fecha_vencimiento") LocalDate fechaVencimiento,
        @JsonProperty("dias_retraso") int diasRetraso,
        @JsonProperty("en_plazo") boolean enPlazo,
        String estado,
        @JsonProperty("precio_copia") BigDecimal precioCopia
) {}
