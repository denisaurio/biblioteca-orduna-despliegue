package com.biblioteca.controller;

import com.biblioteca.dto.DevolucionResponseDTO;
import com.biblioteca.dto.NuevaDevolucionDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Módulo Operaciones - Devoluciones y cálculo de multa (RF07 / RF08). Bibliotecario/admin. */
@RestController
@RequestMapping("/api/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;

    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    /**
     * RF07/RF08 - POST /api/devoluciones
     * Body: {"id_prestamo":1,"observaciones":"Buen estado","estado_ejemplar":"DISPONIBLE","metodo_pago":"EFECTIVO"}
     * Quien devuelve lo saca del token: nunca del cuerpo de la petición.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevolucionResponseDTO registrar(@Valid @RequestBody NuevaDevolucionDTO datos,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return devolucionService.registrarDevolucion(datos, actor.id());
    }
}
