package com.biblioteca.controller;

import com.biblioteca.dto.*;
import com.biblioteca.entity.Rol;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.SancionService;
import com.biblioteca.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Módulo Usuarios (RF04) y suspensión (RF17). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SancionService sancionService;

    public UsuarioController(UsuarioService usuarioService, SancionService sancionService) {
        this.usuarioService = usuarioService;
        this.sancionService = sancionService;
    }

    /** RF04 - POST /api/usuarios/registro (público). Siempre crea un LECTOR. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO registrarse(@Valid @RequestBody RegistroUsuarioDTO datos) {
        // Auto-registro: no hay actor (el lector se registra solo).
        return usuarioService.registrarLector(datos, null);
    }

    /** RF04 - POST /api/usuarios ("+ Registrar usuario", bibliotecario/gerente). Crea un LECTOR. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO registrar(@Valid @RequestBody RegistroUsuarioDTO datos,
                                        @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarioService.registrarLector(datos, actor.id());
    }

    /** GET /api/usuarios?q=ana&rol=LECTOR&pagina=0&tamanio=10 (bibliotecario/gerente) */
    @GetMapping
    public PaginaDTO<UsuarioResponseDTO> listar(@RequestParam(required = false, defaultValue = "") String q,
                                                @RequestParam(required = false) Rol rol,
                                                @RequestParam(required = false, defaultValue = "0") Integer pagina,
                                                @RequestParam(required = false, defaultValue = "10") Integer tamanio) {
        return usuarioService.listarUsuarios(q, rol, pagina, tamanio);
    }

    /** GET /api/usuarios/me - mi perfil (cualquier rol) */
    @GetMapping("/me")
    public UsuarioResponseDTO yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return usuarioService.obtenerUsuario(usuario.id());
    }

    /** PUT /api/usuarios/me - editar mi perfil (cualquier rol) */
    @PutMapping("/me")
    public UsuarioResponseDTO actualizarYo(@Valid @RequestBody ActualizarUsuarioDTO datos,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return usuarioService.actualizarUsuario(usuario.id(), datos, usuario.id());
    }

    /** GET /api/usuarios/{idUsuario} (bibliotecario/gerente) */
    @GetMapping("/{idUsuario}")
    public UsuarioResponseDTO obtener(@PathVariable Long idUsuario) {
        return usuarioService.obtenerUsuario(idUsuario);
    }

    /** PUT /api/usuarios/{idUsuario} (bibliotecario edita lectores; gerente a cualquiera) */
    @PutMapping("/{idUsuario}")
    public UsuarioResponseDTO actualizar(@PathVariable Long idUsuario, @Valid @RequestBody ActualizarUsuarioDTO datos,
                                         @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarioService.actualizarUsuario(idUsuario, datos, actor.id());
    }

    /**
     * RF17 - POST /api/usuarios/{idUsuario}/suspender (bibliotecario/gerente)
     * Body: {"motivo":"Retraso en devolución","fecha_fin":"2026-10-15"}
     * El responsable es el usuario logueado (JWT).
     */
    @PostMapping("/{idUsuario}/suspender")
    @ResponseStatus(HttpStatus.CREATED)
    public SancionResponseDTO suspender(@PathVariable Long idUsuario, @Valid @RequestBody SancionDTO datos,
                                        @AuthenticationPrincipal UsuarioAutenticado bibliotecario) {
        return sancionService.aplicarSancion(idUsuario, datos, bibliotecario.id());
    }

    /** GET /api/usuarios/{idUsuario}/sanciones - historial de sanciones de un usuario */
    @GetMapping("/{idUsuario}/sanciones")
    public List<SancionResponseDTO> sanciones(@PathVariable Long idUsuario) {
        return sancionService.sancionesDeUsuario(idUsuario);
    }
}
