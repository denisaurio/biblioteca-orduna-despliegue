package com.biblioteca.controller;

import com.biblioteca.dto.FichaClienteDTO;
import com.biblioteca.dto.NotaClienteDTO;
import com.biblioteca.dto.NuevaNotaClienteDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.CrmService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CRM del administrador: Ficha 360 del cliente y notas internas. Solo gerente (SecurityConfig). */
@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crmService;

    public CrmController(CrmService crmService) {
        this.crmService = crmService;
    }

    /** GET /api/crm/clientes/{id} - Ficha 360: datos, resumen, préstamos con sus cajas, multas, pagos, sanciones y notas. */
    @GetMapping("/clientes/{idCliente}")
    public FichaClienteDTO ficha(@PathVariable Long idCliente) {
        return crmService.ficha(idCliente);
    }

    /** POST /api/crm/clientes/{id}/notas - Body: {"texto":"Llamó, paga el viernes"} */
    @PostMapping("/clientes/{idCliente}/notas")
    @ResponseStatus(HttpStatus.CREATED)
    public NotaClienteDTO agregarNota(@PathVariable Long idCliente, @Valid @RequestBody NuevaNotaClienteDTO datos,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return crmService.agregarNota(idCliente, datos, actor.id());
    }

    /** DELETE /api/crm/notas/{id} */
    @DeleteMapping("/notas/{idNota}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarNota(@PathVariable Long idNota, @AuthenticationPrincipal UsuarioAutenticado actor) {
        crmService.borrarNota(idNota, actor.id());
    }
}
