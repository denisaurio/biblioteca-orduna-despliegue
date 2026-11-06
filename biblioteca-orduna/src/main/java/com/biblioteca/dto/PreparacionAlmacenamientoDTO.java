package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resultado de preparar el almacenamiento de archivos (Cloudflare R2).
 *
 * Dice que buckets se crearon en esta llamada y como queda el almacenamiento
 * despues, para que el panel lo muestre sin tener que recargar.
 */
public record PreparacionAlmacenamientoDTO(
        /** Los buckets que se crearon ahora (vacio si ya existian). */
        @JsonProperty("creados") List<String> creados,
        /** Como queda el almacenamiento despues de preparar. */
        @JsonProperty("estado") EstadoAlmacenamientoDTO estado
) {}
