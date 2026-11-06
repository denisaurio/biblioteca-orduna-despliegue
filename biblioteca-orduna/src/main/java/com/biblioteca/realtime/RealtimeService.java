package com.biblioteca.realtime;

import com.biblioteca.entity.Rol;
import com.biblioteca.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canal de actualizaciones en tiempo real (SSE).
 *
 * Cada navegador autenticado abre una conexión larga en GET /api/eventos.
 * El backend guarda los emisores y les empuja eventos:
 *
 * - "cambio": se publica automáticamente al terminar con éxito cualquier
 *   POST/PUT/PATCH/DELETE de la API (ver {@link RealtimeMutationFilter}). No
 *   lleva datos: solo dice qué recurso cambió para que el frontend recargue.
 * - "notificacion": se publica desde NotificacionService y va SOLO al usuario
 *   destinatario, para que la campanita y la pantalla de avisos se actualicen.
 *
 * No se usa ninguna dependencia nueva: SSE es parte de Spring MVC.
 */
@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private static final long LATIDO_MS = 25_000L;

    private final List<Suscripcion> suscripciones = new CopyOnWriteArrayList<>();

    /** Abre el canal SSE de un usuario ya autenticado. */
    public SseEmitter suscribir(UsuarioAutenticado usuario) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = sin timeout; el latido evita que el proxy lo corte
        Suscripcion suscripcion = new Suscripcion(emitter, usuario.id(), usuario.rol());
        suscripciones.add(suscripcion);

        emitter.onCompletion(() -> suscripciones.remove(suscripcion));
        emitter.onTimeout(() -> {
            suscripciones.remove(suscripcion);
            emitter.complete();
        });
        emitter.onError(e -> suscripciones.remove(suscripcion));

        suscripcion.enviar(EventoRealtime.conectado(usuario.id(), usuario.rol().name()));
        log.debug("SSE conectado: usuario={} rol={} conexiones={}",
                usuario.id(), usuario.rol(), suscripciones.size());
        return emitter;
    }

    /**
     * Avisa a todos los navegadores conectados de que un recurso cambió.
     * El evento no incluye datos: cada pantalla decide si le afecta y recarga.
     */
    public void publicarCambio(String recurso, String accion) {
        if (recurso == null || recurso.isBlank()) {
            return;
        }
        EventoRealtime evento = EventoRealtime.cambio(recurso, accion);
        for (Suscripcion s : suscripciones) {
            if (!s.enviar(evento)) {
                quitar(s);
            }
        }
    }

    /** Envía un aviso nuevo solo a su destinatario. */
    public void publicarNotificacion(Long idUsuario, Long id, String tipo, String mensaje,
                                     Long idReferencia, LocalDateTime fechaCreacion) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", id);
        datos.put("tipo", tipo);
        datos.put("mensaje", mensaje);
        datos.put("id_referencia", idReferencia);
        datos.put("fecha_creacion", fechaCreacion);
        datos.put("leida", false);
        despuesDeCommit(() -> enviarAUsuario(idUsuario, EventoRealtime.notificacion("CREAR", datos)));
    }

    /** Avisa al usuario que su contador de no leídas cambió (marcar leída/todas). */
    public void publicarNotificacionesActualizadas(Long idUsuario) {
        despuesDeCommit(() -> enviarAUsuario(idUsuario,
                EventoRealtime.notificacion("ACTUALIZAR", null)));
    }

    /** Latido periódico: mantiene viva la conexión a través de proxies y detecta emisores muertos. */
    @Scheduled(fixedRate = LATIDO_MS)
    public void latir() {
        for (Suscripcion s : suscripciones) {
            if (!s.latir()) {
                quitar(s);
            }
        }
    }

    @PreDestroy
    public void cerrarTodas() {
        for (Suscripcion s : suscripciones) {
            s.emitter.complete();
        }
        suscripciones.clear();
    }

    private void enviarAUsuario(Long idUsuario, EventoRealtime evento) {
        for (Suscripcion s : suscripciones) {
            if (s.idUsuario.equals(idUsuario) && !s.enviar(evento)) {
                quitar(s);
            }
        }
    }

    private void quitar(Suscripcion s) {
        suscripciones.remove(s);
        // No se llama a emitter.complete(): la conexión ya falló o el navegador la
        // cerró, y completarla desde el hilo del latido lanza otra excepción asíncrona
        // (AsyncRequestNotUsableException). Tomcat termina solo el contexto async.
    }

    /**
     * Si hay una transacción abierta, el evento sale después del commit:
     * así el navegador nunca recarga antes de que el dato esté guardado y
     * no se anuncia una notificación que luego se revierte.
     */
    private void despuesDeCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    /** Una conexión abierta de un navegador. */
    private static final class Suscripcion {
        private final SseEmitter emitter;
        private final Long idUsuario;
        private final Rol rol;

        private Suscripcion(SseEmitter emitter, Long idUsuario, Rol rol) {
            this.emitter = emitter;
            this.idUsuario = idUsuario;
            this.rol = rol;
        }

        private synchronized boolean enviar(EventoRealtime evento) {
            try {
                emitter.send(SseEmitter.event().data(evento, MediaType.APPLICATION_JSON));
                return true;
            } catch (IOException | RuntimeException e) {
                // Conexión cerrada o evento que no se pudo serializar: se descarta.
                return false;
            }
        }

        private synchronized boolean latir() {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }
    }
}
