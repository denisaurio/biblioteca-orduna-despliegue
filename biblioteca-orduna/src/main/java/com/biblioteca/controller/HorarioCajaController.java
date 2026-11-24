package com.biblioteca.controller;

import com.biblioteca.dto.GuardarHorariosCajaDTO;
import com.biblioteca.dto.HorarioCajaResponseDTO;
import com.biblioteca.dto.MiHorarioCajaDTO;
import com.biblioteca.dto.UsuarioResponseDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.HorarioCajaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Configuración del horario de la caja: la hace el gerente y el personal la
 * consulta para saber a qué hora se le cierra la caja que atiende.
 */
@RestController
@RequestMapping("/api/configuracion-caja")
public class HorarioCajaController {

    private final HorarioCajaService horarioCajaService;

    public HorarioCajaController(HorarioCajaService horarioCajaService) {
        this.horarioCajaService = horarioCajaService;
    }

    /** GET /api/configuracion-caja - todos los horarios (solo gerente). */
    @GetMapping
    public List<HorarioCajaResponseDTO> listar() {
        return horarioCajaService.listar();
    }

    /** GET /api/configuracion-caja/encargados - bibliotecarios activos (solo gerente). */
    @GetMapping("/encargados")
    public List<UsuarioResponseDTO> encargados() {
        return horarioCajaService.encargados();
    }

    /** GET /api/configuracion-caja/mi-horario - mis horarios y el de hoy. */
    @GetMapping("/mi-horario")
    public MiHorarioCajaDTO miHorario(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return horarioCajaService.miHorario(usuario.id());
    }

    /** PUT /api/configuracion-caja - reemplaza todos los horarios (solo gerente). */
    @PutMapping
    public List<HorarioCajaResponseDTO> guardar(@Valid @RequestBody GuardarHorariosCajaDTO datos,
                                                @AuthenticationPrincipal UsuarioAutenticado actor) {
        return horarioCajaService.guardar(datos, actor.id());
    }
}
