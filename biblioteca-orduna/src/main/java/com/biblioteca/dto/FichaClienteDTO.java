package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/crm/clientes/{id} - Ficha 360 del cliente (solo el gerente).
 * Todo de un lector en una pantalla: datos, resumen, préstamos con su caja de inicio y
 * de fin, multas con la caja donde se pagaron, pagos, sanciones y notas internas.
 */
public record FichaClienteDTO(
        UsuarioResponseDTO cliente,
        Resumen resumen,
        List<PrestamoResponseDTO> prestamos,
        List<MultaResponseDTO> multas,
        List<CajaMovimientoDTO> pagos,
        List<SancionResponseDTO> sanciones,
        List<NotaClienteDTO> notas
) {
    /**
     * total_pagado: todo el dinero que entró a caja por este cliente.
     * deuda: saldo de multas pendientes o parciales.
     * a_tiempo_pct: % de devoluciones sin retraso (null si aún no devolvió ninguno).
     */
    public record Resumen(
            @JsonProperty("total_prestamos") long totalPrestamos,
            long activos,
            long vencidos,
            @JsonProperty("por_cobrar") long porCobrar,
            long devueltos,
            @JsonProperty("devueltos_a_tiempo") long devueltosATiempo,
            @JsonProperty("a_tiempo_pct") Integer aTiempoPct,
            @JsonProperty("total_pagado") BigDecimal totalPagado,
            @JsonProperty("pagado_prestamos") BigDecimal pagadoPrestamos,
            @JsonProperty("pagado_multas") BigDecimal pagadoMultas,
            @JsonProperty("pagado_virtual") BigDecimal pagadoVirtual,
            BigDecimal deuda,
            @JsonProperty("multas_total") BigDecimal multasTotal,
            @JsonProperty("ultima_actividad") LocalDateTime ultimaActividad,
            @JsonProperty("sancion_activa") boolean sancionActiva
    ) {}
}
