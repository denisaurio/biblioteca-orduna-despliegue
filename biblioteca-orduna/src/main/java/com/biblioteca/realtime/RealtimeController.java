package com.biblioteca.realtime;

import com.biblioteca.exception.CredencialesInvalidasException;
import com.biblioteca.security.JwtService;
import com.biblioteca.security.UsuarioAutenticado;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /api/eventos - canal de actualizaciones en tiempo real.
 *
 * EventSource (la API del navegador) no permite enviar cabeceras, así que el
 * JWT se acepta también como ?token=. La conexión se valida aquí, porque el
 * endpoint es público en SecurityConfig (si no, el filtro exigiría el header
 * Authorization que EventSource no puede mandar).
 */
@RestController
@RequestMapping("/api/eventos")
public class RealtimeController {

    private final JwtService jwtService;
    private final RealtimeService realtimeService;

    public RealtimeController(JwtService jwtService, RealtimeService realtimeService) {
        this.jwtService = jwtService;
        this.realtimeService = realtimeService;
    }

    @GetMapping
    public SseEmitter eventos(@RequestParam(name = "token", required = false) String token,
                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                              HttpServletResponse response) {
        String jwt = token;
        if ((jwt == null || jwt.isBlank()) && authorization != null
                && authorization.startsWith("Bearer ")) {
            jwt = authorization.substring("Bearer ".length()).trim();
        }
        if (jwt == null || jwt.isBlank()) {
            throw new CredencialesInvalidasException(
                    "Debe iniciar sesión para recibir actualizaciones en tiempo real");
        }

        UsuarioAutenticado usuario;
        try {
            usuario = jwtService.validarToken(jwt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CredencialesInvalidasException("Token inválido o expirado");
        }

        // Evita que proxies (Traefik/Nginx) guarden en búfer el stream.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return realtimeService.suscribir(usuario);
    }
}
