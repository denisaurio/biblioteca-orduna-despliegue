package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una caja (el cajón de la biblioteca).
 *
 * total_cobrado: todo lo que entró en esta caja, por cualquier método de pago.
 * esperado_efectivo: lo que debería haber en el cajón (monto inicial + efectivo cobrado).
 * diferencia: sobrante (positivo) o faltante (negativo) al arquear.
 * encargado: el empleado que cobra ahora; encargados: historial (quién, desde, hasta).
 * monto_contado_encargado: el efectivo que contó el encargado (arqueo), antes de que el gerente cierre.
 */
public record CajaResponseDTO(
        Long id,
        @JsonProperty("fecha_apertura") LocalDateTime fechaApertura,
        @JsonProperty("fecha_cierre") LocalDateTime fechaCierre,
        @JsonProperty("nombre_usuario_apertura") String nombreUsuarioApertura,
        @JsonProperty("nombre_usuario_cierre") String nombreUsuarioCierre,
        @JsonProperty("monto_inicial") BigDecimal montoInicial,
        @JsonProperty("monto_final_declarado") BigDecimal montoFinalDeclarado,
        @JsonProperty("esperado_efectivo") BigDecimal esperadoEfectivo,
        BigDecimal diferencia,
        String estado,
        String observaciones,
        @JsonProperty("total_cobrado") BigDecimal totalCobrado,
        @JsonProperty("id_encargado") Long idEncargado,
        @JsonProperty("nombre_encargado") String nombreEncargado,
        @JsonProperty("monto_contado_encargado") BigDecimal montoContadoEncargado,
        @JsonProperty("fecha_arqueo") LocalDateTime fechaArqueo,
        @JsonProperty("nombre_usuario_arqueo") String nombreUsuarioArqueo,
        List<EncargadoCajaDTO> encargados
) {}
