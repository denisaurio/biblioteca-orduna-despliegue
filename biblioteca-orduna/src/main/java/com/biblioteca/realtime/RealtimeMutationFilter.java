package com.biblioteca.realtime;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Publica un evento "cambio" cuando una petición de escritura de la API termina bien.
 *
 * Así no hay que acordarse de emitir un evento en cada controlador: cualquier
 * POST/PUT/PATCH/DELETE exitoso avisa a los navegadores conectados de que ese
 * recurso cambió y las pantallas interesadas se recargan solas.
 *
 * Se excluyen:
 *  - /api/eventos: es el propio canal SSE.
 *  - /api/notificaciones: sus cambios se envían dirigidos al usuario dueño
 *    desde NotificacionService (no deben difundirse a todos).
 *  - /api/auth: login/logout no modifican datos de negocio.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RealtimeMutationFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITURA =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RealtimeService realtimeService;

    public RealtimeMutationFilter(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (debePublicar(request, response)) {
                realtimeService.publicarCambio(recursoDe(request.getRequestURI()), request.getMethod());
            }
        }
    }

    private static boolean debePublicar(HttpServletRequest request, HttpServletResponse response) {
        if (!METODOS_ESCRITURA.contains(request.getMethod())) {
            return false;
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return false;
        }
        String recurso = recursoDe(request.getRequestURI());
        return recurso != null
                && !recurso.equals("auth")
                && !recurso.equals("eventos")
                && !recurso.equals("notificaciones");
    }

    /** /api/reservas/5/cancelar -> "reservas"; /api/cierre-diario -> "cierre-diario". */
    private static String recursoDe(String ruta) {
        if (ruta == null || !ruta.startsWith("/api/")) {
            return null;
        }
        String resto = ruta.substring("/api/".length());
        int barra = resto.indexOf('/');
        String recurso = barra < 0 ? resto : resto.substring(0, barra);
        return recurso.isBlank() ? null : recurso;
    }
}
