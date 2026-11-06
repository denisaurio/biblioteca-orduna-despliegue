package com.biblioteca.entity;

/**
 * Qué se puede auditar. Cada acción es una operación del personal (o del sistema)
 * que queda registrada con su autor, su fecha y una descripción legible.
 *
 * Solo el gerente ve la auditoría (ver SecurityConfig y AuditoriaController).
 */
public enum TipoAccionAuditoria {

    // Catálogo
    REGISTRAR_LIBRO,
    EDITAR_LIBRO,
    ESCANEAR_ISBN,
    SUBIR_PORTADA,
    SUBIR_PDF,
    REGISTRAR_EJEMPLAR,
    EDITAR_EJEMPLAR,

    // Operaciones
    REGISTRAR_PRESTAMO,
    REGISTRAR_DEVOLUCION,
    COBRAR_MULTA,

    // Caja
    ABRIR_CAJA,
    CERRAR_CAJA,

    // Turnos (bitácora del personal)
    ABRIR_TURNO,
    CERRAR_TURNO,

    // Libro virtual
    ACEPTAR_COMPROBANTE,
    DENEGAR_COMPROBANTE,
    COBRAR_PEDIDO_VIRTUAL,

    // Usuarios, sanciones y avisos
    REGISTRAR_USUARIO,
    EDITAR_USUARIO,
    APLICAR_SANCION,
    LEVANTAR_SANCION,
    CREAR_AVISO,

    // Configuración
    CAMBIAR_CONFIGURACION,
    PREPARAR_ALMACENAMIENTO
}
