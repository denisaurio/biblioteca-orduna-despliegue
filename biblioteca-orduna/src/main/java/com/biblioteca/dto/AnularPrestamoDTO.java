package com.biblioteca.dto;

import jakarta.validation.constraints.Size;

/** POST /api/prestamos/{id}/anular - el cliente no pagó: se anula y el ejemplar se libera. */
public record AnularPrestamoDTO(
        @Size(max = 200, message = "El motivo no debe superar los 200 caracteres")
        String motivo
) {}
