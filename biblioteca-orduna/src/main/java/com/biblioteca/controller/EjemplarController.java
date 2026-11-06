package com.biblioteca.controller;

import com.biblioteca.dto.ActualizarEjemplarDTO;
import com.biblioteca.dto.EjemplarResponseDTO;
import com.biblioteca.dto.RegistroEjemplarDTO;
import com.biblioteca.entity.EstadoEjemplar;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.EjemplarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Módulo Ejemplares (RF14). Bibliotecario/gerente. */
@RestController
@RequestMapping("/api")
public class EjemplarController {

    private final EjemplarService ejemplarService;

    public EjemplarController(EjemplarService ejemplarService) {
        this.ejemplarService = ejemplarService;
    }

    /** RF14 - POST /api/libros/{idLibro}/ejemplares  Body: {"codigo_inventario":"INV-001","ubicacion":"Estante A1"} */
    @PostMapping("/libros/{idLibro}/ejemplares")
    @ResponseStatus(HttpStatus.CREATED)
    public EjemplarResponseDTO registrar(@PathVariable Long idLibro, @Valid @RequestBody RegistroEjemplarDTO datos,
                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ejemplarService.registrarEjemplar(idLibro, datos, actor.id());
    }

    /** GET /api/ejemplares?id_libro=1&estado=DISPONIBLE&q=A1 (todos opcionales) */
    @GetMapping("/ejemplares")
    public List<EjemplarResponseDTO> listar(@RequestParam(name = "id_libro", required = false) Long idLibro,
                                            @RequestParam(required = false) EstadoEjemplar estado,
                                            @RequestParam(required = false, defaultValue = "") String q) {
        return ejemplarService.listarEjemplares(idLibro, estado, q);
    }

    /** GET /api/ejemplares/{idEjemplar} - "Ver detalle" */
    @GetMapping("/ejemplares/{idEjemplar}")
    public EjemplarResponseDTO obtener(@PathVariable Long idEjemplar) {
        return ejemplarService.obtenerEjemplar(idEjemplar);
    }

    /** PUT /api/ejemplares/{idEjemplar}  Body: {"ubicacion":"Estante B2","estado":"DANADO"} (dar de baja: "BAJA") */
    @PutMapping("/ejemplares/{idEjemplar}")
    public EjemplarResponseDTO actualizar(@PathVariable Long idEjemplar, @Valid @RequestBody ActualizarEjemplarDTO datos,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ejemplarService.actualizarEjemplar(idEjemplar, datos, actor.id());
    }
}
