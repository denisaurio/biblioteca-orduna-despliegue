package com.biblioteca.controller;

import com.biblioteca.dto.*;
import com.biblioteca.entity.EstadoAccesoVirtual;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.AccesoVirtualService;
import com.biblioteca.service.ComprobanteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Libros virtuales (RF de libros digitales).
 *
 * Del lado del lector: pedir el libro, ver los suyos y abrir el PDF.
 * Del lado del mostrador: buscar la orden por el codigo del cliente y cobrar, cambiar o cancelar.
 */
@RestController
@RequestMapping("/api/accesos-virtuales")
public class AccesoVirtualController {

    private final AccesoVirtualService accesoVirtualService;
    private final ComprobanteService comprobanteService;

    public AccesoVirtualController(AccesoVirtualService accesoVirtualService, ComprobanteService comprobanteService) {
        this.accesoVirtualService = accesoVirtualService;
        this.comprobanteService = comprobanteService;
    }

    /** POST /api/accesos-virtuales - el lector pide un libro virtual (alquiler o compra). */
    @PostMapping
    public PedidoVirtualDTO pedir(@Valid @RequestBody RegistroAccesoVirtualDTO datos,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        return accesoVirtualService.pedir(datos, actor.id());
    }

    /** GET /api/accesos-virtuales/mis-libros - los libros virtuales del lector (perfil). */
    @GetMapping("/mis-libros")
    public List<AccesoVirtualResponseDTO> misLibros(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return accesoVirtualService.misAccesos(actor.id());
    }

    /** POST /api/accesos-virtuales/{id}/comprobante - el lector sube la captura de su pago con Yape. */
    @PostMapping(value = "/{idAcceso}/comprobante", consumes = "multipart/form-data")
    public ComprobantePagoResponseDTO subirComprobante(@PathVariable Long idAcceso,
                                                       @RequestPart("comprobante") MultipartFile comprobante,
                                                       @RequestParam(required = false) String referenciaYape,
                                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprobanteService.subir(idAcceso, comprobante, referenciaYape, actor.id());
    }

    /** GET /api/accesos-virtuales/{idAcceso}/comprobantes - las capturas que subio el lector. */
    @GetMapping("/{idAcceso}/comprobantes")
    public List<ComprobantePagoResponseDTO> misComprobantes(@PathVariable Long idAcceso,
                                                            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return comprobanteService.misComprobantes(idAcceso, actor.id());
    }

    /**
     * GET /api/accesos-virtuales/{idAcceso}/pdf?descargar=false - enlace temporal (15 min)
     * para leer el PDF; con descargar=true el navegador lo guarda (solo compras).
     */
    @GetMapping("/{idAcceso}/pdf")
    public Map<String, String> pdf(@PathVariable Long idAcceso,
                                    @RequestParam(name = "descargar", defaultValue = "false") boolean descargar,
                                    @AuthenticationPrincipal UsuarioAutenticado actor) {
        return Map.of("url", accesoVirtualService.urlPdf(idAcceso, actor.id(), descargar));
    }

    /** POST /api/accesos-virtuales/{idAcceso}/cancelar - el lector cancela el suyo, el personal cualquiera. */
    @PostMapping("/{idAcceso}/cancelar")
    public AccesoVirtualResponseDTO cancelar(@PathVariable Long idAcceso,
                                             @AuthenticationPrincipal UsuarioAutenticado actor) {
        return accesoVirtualService.cancelar(idAcceso, actor.id());
    }

    /** GET /api/accesos-virtuales/pedidos - panel del mostrador: pedidos por cobrar. */
    @GetMapping("/pedidos")
    public List<AccesoVirtualResponseDTO> pedidos(@RequestParam(required = false) EstadoAccesoVirtual estado) {
        return accesoVirtualService.listarPedidos(estado);
    }

    /** GET /api/accesos-virtuales/codigo/{codigo} - el personal busca la orden con el codigo del cliente. */
    @GetMapping("/codigo/{codigo}")
    public AccesoVirtualResponseDTO buscarPorCodigo(@PathVariable String codigo) {
        return accesoVirtualService.buscarPorCodigo(codigo);
    }

    /** GET /api/accesos-virtuales/libro/{idLibro} - quien compro o alquilo un libro. */
    @GetMapping("/libro/{idLibro}")
    public List<AccesoVirtualResponseDTO> deLibro(@PathVariable Long idLibro) {
        return accesoVirtualService.accesosDeLibro(idLibro);
    }

    /** POST /api/accesos-virtuales/{idAcceso}/cambiar - el personal cambia el pedido (tipo o dias). */
    @PostMapping("/{idAcceso}/cambiar")
    public AccesoVirtualResponseDTO cambiar(@PathVariable Long idAcceso,
                                            @Valid @RequestBody CambioAccesoVirtualDTO datos,
                                            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return accesoVirtualService.cambiar(idAcceso, datos, actor.id());
    }

    /** POST /api/accesos-virtuales/{idAcceso}/cobrar - el personal cobra en el mostrador. */
    @PostMapping("/{idAcceso}/cobrar")
    public AccesoVirtualResponseDTO cobrar(@PathVariable Long idAcceso,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return accesoVirtualService.cobrar(idAcceso, actor.id());
    }
}
