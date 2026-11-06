package com.biblioteca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pedido de libro virtual.
 *
 * puede_leer: el lector puede abrir el PDF en la aplicacion (alquiler vigente).
 * puede_descargar: solo si lo compro (la compra permite descargar el PDF).
 * vencido: el alquiler ya termino. En ese caso el libro sale "en plomo" en el perfil.
 */
public record AccesoVirtualResponseDTO(
        Long id,
        @JsonProperty("id_libro") Long idLibro,
        String titulo,
        @JsonProperty("url_imagen") String urlImagen,
        /** Quien tiene el acceso. Lo ve el personal del mostrador para saber a quién se le cobró. */
        @JsonProperty("nombre_usuario") String nombreUsuario,
        @JsonProperty("email_usuario") String emailUsuario,
        String tipo,
        String estado,
        java.math.BigDecimal precio,
        @JsonProperty("dias_alquiler") Integer diasAlquiler,
        @JsonProperty("dias_restantes") long diasRestantes,
        @JsonProperty("fecha_inicio") LocalDate fechaInicio,
        @JsonProperty("fecha_fin") LocalDate fechaFin,
        @JsonProperty("metodo_pago") String metodoPago,
        /** Numero de cola que ve el cliente cuando paga en la tienda. */
        @JsonProperty("numero_cola") String numeroCola,
        /** Codigo que el cliente muestra en el mostrador. Solo si paga en la tienda. */
        @JsonProperty("codigo_retiro") String codigoRetiro,
        @JsonProperty("fecha_codigo_expira") LocalDateTime fechaCodigoExpira,
        @JsonProperty("referencia_pago") String referenciaPago,
        @JsonProperty("fecha_creacion") LocalDateTime fechaCreacion,
        boolean vencido,
        @JsonProperty("puede_leer") boolean puedeLeer,
        @JsonProperty("puede_descargar") boolean puedeDescargar,
        /**
         * Estado del ULTIMO comprobante de Yape del pedido (PENDIENTE, APROBADO o DENEGADO).
         * Null si el lector todavia no subio ninguna captura. Es un dato DERIVADO: el
         * estado del pedido no cambia por subir el comprobante.
         */
        @JsonProperty("estado_comprobante") String estadoComprobante,
        /** true si hay una captura esperando que el personal la revise. */
        @JsonProperty("tiene_comprobante") boolean tieneComprobante
) {}
