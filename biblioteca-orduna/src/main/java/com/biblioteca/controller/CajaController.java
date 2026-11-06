package com.biblioteca.controller;

import com.biblioteca.dto.*;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.CajaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * La caja (el cajón de dinero). Solo el personal la ve: el lector no entra aquí.
 *
 * Es una caja única para toda la biblioteca: se abre con el monto inicial que hay
 * en el cajón y se cierra con el efectivo contado (el sistema dice si sobra o falta).
 */
@RestController
@RequestMapping("/api/cajas")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    /** GET /api/cajas/actual - la caja abierta (si hay) y el resumen de hoy. */
    @GetMapping("/actual")
    public ResumenCajaDTO actual() {
        return cajaService.resumen();
    }

    /** POST /api/cajas - abrir el cajón. Body: {"monto_inicial":50,"observaciones":""} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponseDTO abrir(@Valid @RequestBody AbrirCajaDTO datos,
                                 @AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.abrir(datos, actor.id());
    }

    /** POST /api/cajas/{id}/cerrar - cerrar con el efectivo contado. */
    @PostMapping("/{idCaja}/cerrar")
    public CajaResponseDTO cerrar(@PathVariable Long idCaja, @Valid @RequestBody CerrarCajaDTO datos,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.cerrar(idCaja, datos, actor.id());
    }

    /** GET /api/cajas - historial de cajas (fechas en formato AAAA-MM-DD). */
    @GetMapping
    public List<CajaResponseDTO> historial(@RequestParam(required = false) LocalDate desde,
                                           @RequestParam(required = false) LocalDate hasta) {
        return cajaService.historial(desde, hasta);
    }

    /** GET /api/cajas/pendientes - qué libros faltan (préstamos, reservas y pedidos). */
    @GetMapping("/pendientes")
    public PendientesCajaDTO pendientes() {
        return cajaService.pendientes();
    }

    /** GET /api/cajas/{id} - una caja con sus movimientos. */
    @GetMapping("/{idCaja}")
    public CajaDetalleDTO detalle(@PathVariable Long idCaja) {
        return cajaService.detalle(idCaja);
    }
}
