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

    /** GET /api/cajas/actual - la caja abierta (si hay), el resumen de hoy y si el usuario puede cobrar. */
    @GetMapping("/actual")
    public ResumenCajaDTO actual(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.resumen(actor.id());
    }

    /**
     * POST /api/cajas - abrir el cajón (solo gerente).
     * Body: {"monto_inicial":50,"id_encargado":2,"observaciones":""}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponseDTO abrir(@Valid @RequestBody AbrirCajaDTO datos,
                                 @AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.abrir(datos, actor.id());
    }

    /** PUT /api/cajas/{id}/encargado - cambiar el encargado sin cerrar (solo gerente). Body: {"id_encargado":2} */
    @PutMapping("/{idCaja}/encargado")
    public CajaResponseDTO cambiarEncargado(@PathVariable Long idCaja, @Valid @RequestBody AsignarEncargadoDTO datos,
                                            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.cambiarEncargado(idCaja, datos, actor.id());
    }

    /** POST /api/cajas/{id}/arqueo - el encargado declara el efectivo contado. Body: {"monto_contado":120.50} */
    @PostMapping("/{idCaja}/arqueo")
    public CajaResponseDTO arquear(@PathVariable Long idCaja, @Valid @RequestBody ArqueoCajaDTO datos,
                                   @AuthenticationPrincipal UsuarioAutenticado actor) {
        return cajaService.arquear(idCaja, datos, actor.id());
    }

    /** POST /api/cajas/{id}/cerrar - cerrar (solo gerente); sin monto se usa el del arqueo del encargado. */
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
