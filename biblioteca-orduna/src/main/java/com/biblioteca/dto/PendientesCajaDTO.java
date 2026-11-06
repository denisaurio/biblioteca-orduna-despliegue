package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * "Qué libros faltan": lo que está fuera de la biblioteca y lo que se está esperando.
 *
 * prestamos: préstamos activos (faltan devolver).
 * reservas_por_recoger: reservas con ejemplar apartado (hay que recogerlas antes del plazo).
 * reservas_en_cola: reservas que todavía esperan turno.
 * pedidos_virtuales: pedidos de libro virtual sin cobrar.
 */
public record PendientesCajaDTO(
        List<PrestamoPendienteDTO> prestamos,
        @JsonProperty("reservas_por_recoger") List<ReservaPendienteDTO> reservasPorRecoger,
        @JsonProperty("reservas_en_cola") List<ReservaPendienteDTO> reservasEnCola,
        @JsonProperty("pedidos_virtuales") List<PedidoVirtualPendienteDTO> pedidosVirtuales
) {}
