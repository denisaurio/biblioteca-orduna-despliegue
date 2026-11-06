package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Diagnostico del almacenamiento de archivos (Cloudflare R2).
 * Lo consulta el panel de administracion para saber si las portadas y los PDF
 * se pueden guardar antes de intentar subir algo.
 */
public record EstadoAlmacenamientoDTO(
        /** Hay credenciales de R2 cargadas en el .env. */
        @JsonProperty("configurado") boolean configurado,
        /** Se sabe que base publica usa el sistema para las portadas. */
        @JsonProperty("url_publica_configurada") boolean urlPublicaConfigurada,
        /** Base publica con la que se construyen los links de las portadas. */
        @JsonProperty("url_publica") String urlPublica,
        /** De donde sale esa base: R2_URL_PUBLICO_PORTADAS, R2_DOMINIO_PUBLICO o deducida. */
        @JsonProperty("origen_url_publica") String origenUrlPublica,
        @JsonProperty("bucket_portadas") EstadoBucketDTO bucketPortadas,
        @JsonProperty("bucket_pdfs") EstadoBucketDTO bucketPdfs
) {}
