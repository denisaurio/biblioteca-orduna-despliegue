package com.biblioteca.controller;

import com.biblioteca.dto.ColaReservasDTO;
import com.biblioteca.dto.NuevaReservaDTO;
import com.biblioteca.dto.ReservaMostradorDTO;
import com.biblioteca.dto.ReservaResponseDTO;
import com.biblioteca.entity.EstadoReserva;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Módulo Reservas (RF09). */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    /**
     * RF09 - POST /api/reservas  Body: {"id_libro":1}  (solo LECTOR)
     * El lector es el usuario logueado (JWT): nadie puede reservar a nombre de otro.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponseDTO crear(@Valid @RequestBody NuevaReservaDTO datos,
                                    @AuthenticationPrincipal UsuarioAutenticado lector) {
        return reservaService.crearReserva(datos, lector.id());
    }

    /**
     * RF09 - POST /api/reservas/mostrador  Body: {"id_libro":1,"id_usuario_lector":5}
     * (bibliotecario o gerente): anota a un lector en la cola desde el mostrador.
     */
    @PostMapping("/mostrador")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponseDTO crearEnMostrador(@Valid @RequestBody ReservaMostradorDTO datos,
                                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        return reservaService.crearReservaEnMostrador(datos, actor.id());
    }

    /** GET /api/reservas?estado=PENDIENTE (personal). Sin estado = todas. */
    @GetMapping
    public List<ReservaResponseDTO> listar(@RequestParam(required = false) EstadoReserva estado) {
        return reservaService.listarReservas(estado);
    }

    /** GET /api/reservas/cola/{idLibro} - panel "Cola de reservas" (personal) */
    @GetMapping("/cola/{idLibro}")
    public ColaReservasDTO cola(@PathVariable Long idLibro) {
        return reservaService.colaDeLibro(idLibro);
    }

    /** PATCH /api/reservas/{idReserva}/cancelar - el lector cancela la suya; el personal, cualquiera. */
    @PatchMapping("/{idReserva}/cancelar")
    public ReservaResponseDTO cancelar(@PathVariable Long idReserva,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return reservaService.cancelarReserva(idReserva, usuario.id());
    }
}
