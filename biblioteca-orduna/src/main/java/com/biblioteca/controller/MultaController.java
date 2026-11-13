package com.biblioteca.controller;

import com.biblioteca.dto.DevolucionSinMultaDTO;
import com.biblioteca.dto.MultaResponseDTO;
import com.biblioteca.dto.NuevaMultaDTO;
import com.biblioteca.dto.PagoMultaDTO;
import com.biblioteca.dto.PagoMultaResponseDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.DevolucionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Módulo Pagos de multa (RF15). Bibliotecario/gerente. */
@RestController
@RequestMapping("/api/multas")
public class MultaController {

    private final DevolucionService devolucionService;

    public MultaController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    /** GET /api/multas?estado=PENDIENTES  (PENDIENTES incluye parciales; también PAGADAS o TODAS) */
    @GetMapping
    public List<MultaResponseDTO> listar(@RequestParam(required = false, defaultValue = "PENDIENTES") String estado) {
        return devolucionService.listarMultas(estado);
    }

    /**
     * RF15 - POST /api/multas/{idMulta}/pagar  ("+ Registrar pago")
     * Body: {"monto_pagado":1.00,"metodo_pago":"EFECTIVO"}  (EFECTIVO|TARJETA|TRANSFERENCIA|YAPE|PLIN|OTRO)
     * Acepta pago parcial. El bibliotecario que cobra es el usuario logueado (JWT).
     */
    @PostMapping("/{idMulta}/pagar")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoMultaResponseDTO pagar(@PathVariable Long idMulta, @Valid @RequestBody PagoMultaDTO datos,
                                      @AuthenticationPrincipal UsuarioAutenticado bibliotecario) {
        return devolucionService.registrarPagoMulta(idMulta, datos, bibliotecario.id());
    }

    /** Devoluciones sin multa: alimentan el selector de "+ Registrar multa". */
    @GetMapping("/sin-generar")
    public List<DevolucionSinMultaDTO> sinGenerar() {
        return devolucionService.listarDevolucionesSinMulta();
    }

    /**
     * RF15 - POST /api/multas  ("+ Registrar multa")
     * Body: {"id_devolucion":1,"monto":5.00,"concepto":"Libro dañado"}
     * Genera una multa manual sobre una devolución que no tenía. No cobra: solo crea la deuda.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MultaResponseDTO crear(@Valid @RequestBody NuevaMultaDTO datos,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        return devolucionService.registrarMultaManual(datos, actor.id());
    }

    /** GET /api/multas/pagos?desde=2026-10-01&hasta=2026-10-31 - historial de pagos (auditoría) */
    @GetMapping("/pagos")
    public List<PagoMultaResponseDTO> pagos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return devolucionService.listarPagos(desde, hasta);
    }
}
