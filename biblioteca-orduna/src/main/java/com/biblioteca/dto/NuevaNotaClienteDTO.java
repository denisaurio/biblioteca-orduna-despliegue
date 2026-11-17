package com.biblioteca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/crm/clientes/{id}/notas */
public record NuevaNotaClienteDTO(
        @NotBlank(message = "Escribe la nota")
        @Size(max = 1000, message = "La nota no debe superar los 1000 caracteres")
        String texto
) {}
