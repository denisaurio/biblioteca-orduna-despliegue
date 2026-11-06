package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * RF09 - Body de POST /api/reservas/mostrador (bibliotecario o gerente).
 *
 * El personal anota en la cola a un lector que pide el libro en el mostrador o por
 * telefono. El lector es el DESTINO de la reserva (como en el prestamo), no quien actua:
 * el actor (el bibliotecario) sale del token, nunca de aqui.
 */
public record ReservaMostradorDTO(
        @JsonProperty("id_libro") @NotNull Long idLibro,
        @JsonProperty("id_usuario_lector") @NotNull Long idUsuarioLector
) {}
