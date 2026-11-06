package com.biblioteca.controller;

import com.biblioteca.dto.ComprobantePagoResponseDTO;
import com.biblioteca.dto.RevisionComprobanteDTO;
import com.biblioteca.entity.EstadoComprobante;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.ComprobanteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Comprobantes de pago con Yape (panel del personal).
 *
 * El lector sube la captura desde su pedido (POST /api/accesos-virtuales/{id}/comprobante).
 * Aqui el personal la mira, la acepta o la deniega.
 */
@RestController
@RequestMapping("/api/comprobantes")
public class ComprobantePagoController {

    private final ComprobanteService comprobanteService;

    public ComprobantePagoController(ComprobanteService comprobanteService) {
        this.comprobanteService = comprobanteService;
    }

    /** GET /api/comprobantes/pendientes - los que aun no ha revisado nadie. */
    @GetMapping("/pendientes")
    public List<ComprobantePagoResponseDTO> pendientes() {
        return comprobanteService.listarPendientes();
    }

    /** GET /api/comprobantes - todos los comprobantes (filtro opcional por estado). */
    @GetMapping
    public List<ComprobantePagoResponseDTO> listar(@RequestParam(required = false) EstadoComprobante estado) {
        return comprobanteService.listar(estado);
    }

    /** GET /api/comprobantes/{id}/imagen - enlace temporal a la captura (caduca en 15 minutos). */
    @GetMapping("/{idComprobante}/imagen")
    public Map<String, String> imagen(@PathVariable Long idComprobante,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return Map.of("url", comprobanteService.urlImagen(idComprobante, actor.id()));
    }

    /** POST /api/comprobantes/{id}/aprobar - el lector ya tiene el libro virtual. */
    @PostMapping("/{idComprobante}/aprobar")
    public ComprobantePagoResponseDTO aprobar(@PathVariable Long idComprobante,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprobanteService.aprobar(idComprobante, actor.id());
    }

    /** POST /api/comprobantes/{id}/denegar - el lector puede subir otra captura. */
    @PostMapping("/{idComprobante}/denegar")
    public ComprobantePagoResponseDTO denegar(@PathVariable Long idComprobante,
                                              @Valid @RequestBody RevisionComprobanteDTO datos,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprobanteService.denegar(idComprobante, datos, actor.id());
    }
}
