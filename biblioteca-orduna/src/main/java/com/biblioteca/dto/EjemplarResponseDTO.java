package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record EjemplarResponseDTO(
        Long id,
        @JsonProperty("id_libro") Long idLibro,
        @JsonProperty("titulo_libro") String tituloLibro,
        @JsonProperty("codigo_inventario") String codigoInventario,
        String ubicacion,
        String estado,
        /** Precio de esta copia: el propio, o el del libro si la copia no tiene precio propio. */
        @JsonProperty("precio") BigDecimal precio,
        /** Precio general del libro: es el que se usa si la copia no tiene precio propio. */
        @JsonProperty("precio_libro") BigDecimal precioLibro
) {}
