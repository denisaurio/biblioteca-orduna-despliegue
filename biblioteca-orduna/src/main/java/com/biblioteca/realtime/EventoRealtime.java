package com.biblioteca.realtime;

import java.time.LocalDateTime;

/**
 * Un evento que viaja por SSE (Server-Sent Events) desde el backend al navegador.
 *
 * Tipos:
 *   conectado .......... confirma la suscripción (lleva id y rol del usuario).
 *   cambio ............. una mutación de la API terminó bien; el frontend refresca
 *                        las pantallas suscritas a ese recurso.
 *   notificacion ....... aviso nuevo o cambio de leídos, solo para su destinatario.
 *
 * No lleva datos sensibles: "datos" solo se llena en los eventos de notificación
 * (id, tipo, mensaje y referencia), que se envían únicamente al usuario dueño.
 */
public record EventoRealtime(
        String tipo,
        String recurso,
        String accion,
        Object datos,
        LocalDateTime fecha
) {

    public static EventoRealtime conectado(Long idUsuario, String rol) {
        return new EventoRealtime("conectado", "eventos", "ABRIR",
                new DatosConexion(idUsuario, rol), LocalDateTime.now());
    }

    public static EventoRealtime cambio(String recurso, String accion) {
        return new EventoRealtime("cambio", recurso, accion, null, LocalDateTime.now());
    }

    public static EventoRealtime notificacion(String accion, Object datos) {
        return new EventoRealtime("notificacion", "notificaciones", accion, datos, LocalDateTime.now());
    }

    /** Respuesta inicial de la conexión: el frontend sabe a quién pertenece el canal. */
    public record DatosConexion(Long idUsuario, String rol) {
    }
}
