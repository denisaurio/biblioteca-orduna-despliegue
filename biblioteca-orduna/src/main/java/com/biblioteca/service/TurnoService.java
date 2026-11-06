package com.biblioteca.service;

import com.biblioteca.dto.CierreTurnoDTO;
import com.biblioteca.dto.TurnoResponseDTO;
import com.biblioteca.entity.EstadoTurno;
import com.biblioteca.entity.Rol;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.Turno;
import com.biblioteca.entity.Usuario;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.TurnoPendienteException;
import com.biblioteca.repository.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** RF16 - Bitácora de turnos. */
@Service
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public TurnoService(TurnoRepository turnoRepository, UsuarioService usuarioService,
                        AuditoriaService auditoriaService) {
        this.turnoRepository = turnoRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /** RF16 - Abrir turno. TurnoPendienteException si ya tiene uno abierto. */
    @Transactional
    public TurnoResponseDTO abrirTurno(Long idBibliotecario) {
        Usuario b = usuarioService.buscarPersonal(idBibliotecario);
        turnoRepository.findFirstByBibliotecarioIdAndEstado(idBibliotecario, EstadoTurno.ABIERTO)
                .ifPresent(t -> {
                    throw new TurnoPendienteException("Tiene un turno abierto desde " + t.getFechaApertura()
                            + ". Ciérrelo antes de abrir otro");
                });
        Turno t = new Turno();
        t.setBibliotecario(b);
        t.setFechaApertura(LocalDateTime.now());
        t.setEstado(EstadoTurno.ABIERTO);
        t = turnoRepository.save(t);
        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.ABRIR_TURNO, "TURNO", t.getId(),
                "Abrió su turno");
        return aDTO(t);
    }

    /** RF16 - Cerrar el turno abierto del bibliotecario. */
    @Transactional
    public TurnoResponseDTO cerrarTurno(Long idBibliotecario, CierreTurnoDTO datos) {
        usuarioService.buscarPersonal(idBibliotecario);
        Turno t = turnoRepository.findFirstByBibliotecarioIdAndEstado(idBibliotecario, EstadoTurno.ABIERTO)
                .orElseThrow(() -> new OperacionInvalidaException("No tiene ningún turno abierto para cerrar"));
        t.setFechaCierre(LocalDateTime.now());
        t.setObservaciones(datos == null ? null : datos.observaciones());
        t.setEstado(EstadoTurno.CERRADO);
        t = turnoRepository.save(t);
        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.CERRAR_TURNO, "TURNO", t.getId(),
                "Cerró su turno");
        return aDTO(t);
    }

    /** Turno abierto del usuario logueado (null si no tiene). */
    @Transactional(readOnly = true)
    public TurnoResponseDTO turnoActual(Long idBibliotecario) {
        return turnoRepository.findFirstByBibliotecarioIdAndEstado(idBibliotecario, EstadoTurno.ABIERTO)
                .map(TurnoService::aDTO).orElse(null);
    }

    /**
     * Pantalla Bitácora de turnos con filtros Desde/Hasta (null = mes actual).
     * - Gerente: ve a todo el personal; puede filtrar por id_bibliotecario y por tipo (rol).
     * - Bibliotecario: solo ve sus propios turnos.
     */
    @Transactional(readOnly = true)
    public List<TurnoResponseDTO> listarTurnos(LocalDate desde, LocalDate hasta, Long idBibliotecario,
                                               Rol tipo, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        LocalDate[] r = ReporteService.rango(desde, hasta);
        LocalDateTime ini = r[0].atStartOfDay(), fin = r[1].atTime(LocalTime.MAX);
        Long filtro = actor.getRol() == Rol.GERENTE ? idBibliotecario : actor.getId();
        List<Turno> turnos = filtro == null
                ? turnoRepository.findByFechaAperturaBetweenOrderByFechaAperturaDesc(ini, fin)
                : turnoRepository.findByBibliotecarioIdAndFechaAperturaBetweenOrderByFechaAperturaDesc(filtro, ini, fin);
        return turnos.stream()
                .filter(t -> tipo == null || t.getBibliotecario().getRol() == tipo)
                .map(TurnoService::aDTO).toList();
    }

    private static TurnoResponseDTO aDTO(Turno t) {
        Usuario b = t.getBibliotecario();
        return new TurnoResponseDTO(t.getId(), b.getId(), b.getNombreCompleto(), b.getRol().name(),
                t.getFechaApertura(), t.getFechaCierre(), t.getObservaciones(), t.getEstado().name());
    }
}
