package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Estado de un bucket de Cloudflare R2, para el panel de administracion.
 *
 * accesible: se puede leer y escribir en el con las credenciales actuales.
 * existe: el bucket esta creado en la cuenta de R2.
 */
public record EstadoBucketDTO(
        String nombre,
        @JsonProperty("existe") boolean existe,
        @JsonProperty("accesible") boolean accesible,
        String detalle
) {}
