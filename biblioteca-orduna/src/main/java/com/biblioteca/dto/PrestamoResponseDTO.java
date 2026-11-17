package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * estado: ACTIVO, VENCIDO (activo y pasado de fecha) o DEVUELTO.
 * en_plazo: false cuando está vencido ("Retraso" en la pantalla Devoluciones).
 * precio_copia: precio del ejemplar cuando se prestó (copiado, no se recalcula).
 * tipo_prestamo: FECHA_FIJA (fijo), PERSONALIZADO o ABIERTO (solo préstamos antiguos).
 * dias_prestamo: días que se prestó el libro.
 * tarifa_multa_diaria: multa por cada día de retraso (copiada en el préstamo).
 * tarifa_personalizada: true si la tarifa la eligió el personal y no la configuración.
 * multa_acumulada: si sigue activo y con retraso, cuánto va de multa hoy (días × tarifa).
 * estado también puede ser POR_COBRAR (falta que el encargado cobre) o ANULADO.
 * id_caja_inicio / id_caja_fin: caja en que empezó (se cobró) y caja en que se devolvió.
 * cobro_al_prestar: true si el precio se cobra al prestar; false en préstamos antiguos (al devolver).
 * id_caja_cobro: caja donde se pagó el precio; fecha_devolucion: cuándo volvió el libro.
 *   Estos dos solo vienen llenos en el detalle de caja y en la Ficha 360.
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
        @JsonProperty("precio_copia") BigDecimal precioCopia,
        @JsonProperty("dias_prestamo") Integer diasPrestamo,
        @JsonProperty("tarifa_multa_diaria") BigDecimal tarifaMultaDiaria,
        @JsonProperty("tarifa_personalizada") boolean tarifaPersonalizada,
        @JsonProperty("multa_acumulada") BigDecimal multaAcumulada,
        @JsonProperty("id_caja_inicio") Long idCajaInicio,
        @JsonProperty("id_caja_fin") Long idCajaFin,
        @JsonProperty("cobro_al_prestar") boolean cobroAlPrestar,
        @JsonProperty("id_caja_cobro") Long idCajaCobro,
        @JsonProperty("fecha_devolucion") LocalDateTime fechaDevolucion,
        @JsonProperty("nombre_bibliotecario") String nombreBibliotecario
) {}
