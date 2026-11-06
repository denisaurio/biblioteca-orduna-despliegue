package com.biblioteca.dto;

/**
 * Resultado de pedir un libro virtual: la orden creada.
 *
 * El pago en linea es con Yape por QR: el lector ve el QR y el monto, paga en su app
 * y sube la captura (POST /api/accesos-virtuales/{id}/comprobante). Si paga en la
 * tienda, el codigo de retiro va en el acceso.
 */
public record PedidoVirtualDTO(
        AccesoVirtualResponseDTO acceso
) {}
