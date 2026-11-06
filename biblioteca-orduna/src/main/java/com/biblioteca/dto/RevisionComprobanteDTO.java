package com.biblioteca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo por el que el personal rechaza un comprobante de Yape.
 * El lector lo ve en su aviso, asi que tiene que ser claro.
 */
public record RevisionComprobanteDTO(
        @NotBlank(message = "Escribe el motivo del rechazo")
        @Size(max = 300, message = "El motivo no debe superar los 300 caracteres")
        String motivo
) {}
