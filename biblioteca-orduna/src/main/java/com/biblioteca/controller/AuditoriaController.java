package com.biblioteca.controller;

import com.biblioteca.dto.AuditoriaResponseDTO;
import com.biblioteca.dto.PaginaDTO;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Auditoría: qué hizo cada persona del personal. Solo el gerente (ver SecurityConfig).
 *
 * GET /api/auditoria?desde=&hasta=&id_usuario=&accion=&pagina=&tamanio=
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /** GET /api/auditoria - historial de acciones, paginado y filtrable. */
    @GetMapping
    public PaginaDTO<AuditoriaResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long idUsuario,
            @RequestParam(required = false) TipoAccionAuditoria accion,
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @RequestParam(required = false, defaultValue = "20") Integer tamanio) {
        return auditoriaService.listar(desde, hasta, idUsuario, accion, pagina, tamanio);
    }
}
