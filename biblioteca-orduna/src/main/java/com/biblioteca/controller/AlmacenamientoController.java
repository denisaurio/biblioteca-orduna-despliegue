package com.biblioteca.controller;

import com.biblioteca.dto.EstadoAlmacenamientoDTO;
import com.biblioteca.dto.PreparacionAlmacenamientoDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.AlmacenamientoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Almacenamiento de archivos (Cloudflare R2).
 *
 * GET /api/almacenamiento/estado dice si las credenciales sirven y si los buckets
 * de portadas y de PDF estan listos. Lo usa el panel de administracion para
 * avisar al bibliotecario antes de que intente subir una portada.
 *
 * POST /api/almacenamiento/preparar crea en R2 los buckets que falten, para que
 * el gerente no tenga que meterse al panel de Cloudflare.
 */
@RestController
@RequestMapping("/api/almacenamiento")
public class AlmacenamientoController {

    private final AlmacenamientoService almacenamientoService;

    public AlmacenamientoController(AlmacenamientoService almacenamientoService) {
        this.almacenamientoService = almacenamientoService;
    }

    /** GET /api/almacenamiento/estado */
    @GetMapping("/estado")
    public EstadoAlmacenamientoDTO estado() {
        return almacenamientoService.estado();
    }

    /**
     * POST /api/almacenamiento/probar-escritura - comprueba que el token de R2 puede
     * subir archivos (sube un objeto vacio de prueba). Es una accion explicita del
     * administrador: la aplicacion no escribe nada en el bucket por su cuenta.
     */
    @PostMapping("/probar-escritura")
    public Map<String, Object> probarEscritura() {
        boolean escribe = almacenamientoService.puedeEscribir();
        return Map.of(
                "escritura", escribe ? "OK" : "DENEGADO",
                "detalle", escribe
                        ? "El token puede subir portadas y PDFs. Se dejo un archivo vacio de prueba en el bucket."
                        : "El token no tiene permiso de escritura: revisa los permisos del token de R2");
    }

    /**
     * POST /api/almacenamiento/preparar - crea en R2 los buckets que falten (el
     * publico de portadas y el privado de los PDF y comprobantes).
     *
     * El actor sale del token (regla 2: nunca del cuerpo de la peticion) y la accion
     * queda auditada por el servicio, en la misma transaccion.
     */
    @PostMapping("/preparar")
    public PreparacionAlmacenamientoDTO preparar(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return almacenamientoService.preparar(actor.id());
    }
}
