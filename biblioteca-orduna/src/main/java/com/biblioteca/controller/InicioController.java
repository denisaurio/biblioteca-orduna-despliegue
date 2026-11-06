package com.biblioteca.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/salud (público): comprueba que la API está funcionando.
 * Lo usa el healthcheck de Docker/Coolify. (Antes estaba en GET /, pero en producción
 * esa ruta es la página del frontend.)
 */
@RestController
public class InicioController {

    @GetMapping("/api/salud")
    public Map<String, Object> inicio() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("api", "Sistema de Biblioteca Orduña");
        r.put("estado", "funcionando");
        r.put("hora", LocalDateTime.now());
        r.put("login", "POST /api/auth/login");
        r.put("catalogo_publico", "GET /api/libros");
        return r;
    }
}
