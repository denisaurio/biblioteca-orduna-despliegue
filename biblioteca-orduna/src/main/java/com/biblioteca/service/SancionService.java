package com.biblioteca.service;

import com.biblioteca.dto.SancionDTO;
import com.biblioteca.dto.SancionResponseDTO;
import com.biblioteca.entity.Rol;
import com.biblioteca.entity.Sancion;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.Usuario;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.exception.UsuarioSancionadoException;
import com.biblioteca.repository.SancionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RF17 - Sanciones y bloqueos. Las aplica y levanta el bibliotecario o el gerente.
 * Mientras una sanción esté activa el lector no puede iniciar sesión, pedir préstamos ni reservar.
 * Pasa a FINALIZADA sola al cumplirse fecha_fin, o antes si se levanta.
 */
@Service
public class SancionService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SancionRepository sancionRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public SancionService(SancionRepository sancionRepository, UsuarioService usuarioService,
                          AuditoriaService auditoriaService) {
        this.sancionRepository = sancionRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /** RF17 - Suspender a un lector hasta fecha_fin (inclusive). */
    @Transactional
    public SancionResponseDTO aplicarSancion(Long idUsuario, SancionDTO datos, Long idBibliotecario) {
        Usuario responsable = usuarioService.buscarPersonal(idBibliotecario);
        Usuario usuario = usuarioService.buscar(idUsuario);
        if (usuario.getRol() != Rol.LECTOR) {
            throw new OperacionInvalidaException("Solo se puede sancionar a lectores");
        }
        if (datos.fechaFin().isBefore(LocalDate.now())) {
            throw new OperacionInvalidaException("La fecha_fin no puede ser anterior a hoy");
        }
        Sancion s = new Sancion();
        s.setUsuario(usuario);
        s.setBibliotecario(responsable);
        s.setMotivo(datos.motivo().trim());
        s.setFechaInicio(LocalDateTime.now());
        s.setFechaFin(datos.fechaFin());
        s = sancionRepository.save(s);
        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.APLICAR_SANCION, "SANCION", s.getId(),
                "Sancionó a " + usuario.getNombreCompleto() + " hasta el " + datos.fechaFin()
                        + ": " + s.getMotivo());
        return aDTO(s);
    }

    /** Levantar (finalizar antes de tiempo) una sanción activa. */
    @Transactional
    public SancionResponseDTO levantarSancion(Long idSancion, Long idBibliotecario) {
        Usuario responsable = usuarioService.buscarPersonal(idBibliotecario);
        Sancion s = sancionRepository.findById(idSancion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la sanción " + idSancion));
        if (!s.estaActiva(LocalDate.now())) {
            throw new OperacionInvalidaException("La sanción " + idSancion + " ya está finalizada");
        }
        s.setFechaLevantamiento(LocalDateTime.now());
        s.setLevantadoPor(responsable);
        s = sancionRepository.save(s);
        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.LEVANTAR_SANCION, "SANCION", s.getId(),
                "Levantó la sanción de " + s.getUsuario().getNombreCompleto());
        return aDTO(s);
    }

    /** Pantalla "Sanciones y bloqueos". soloActivas = true muestra solo las vigentes. */
    @Transactional(readOnly = true)
    public List<SancionResponseDTO> listarSanciones(boolean soloActivas) {
        List<Sancion> lista = soloActivas
                ? sancionRepository.findByFechaFinGreaterThanEqualAndFechaLevantamientoIsNullOrderByFechaFinAsc(LocalDate.now())
                : sancionRepository.findAllByOrderByFechaInicioDesc();
        return lista.stream().map(SancionService::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SancionResponseDTO> sancionesDeUsuario(Long idUsuario) {
        return sancionRepository.findByUsuarioIdOrderByFechaInicioDesc(idUsuario).stream()
                .map(SancionService::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public boolean tieneSancionActiva(Long idUsuario) {
        return sancionRepository.existsByUsuarioIdAndFechaFinGreaterThanEqualAndFechaLevantamientoIsNull(
                idUsuario, LocalDate.now());
    }

    /** Lanza UsuarioSancionadoException (HTTP 403) si el usuario está suspendido. */
    @Transactional(readOnly = true)
    public void verificarSinSancion(Long idUsuario) {
        sancionRepository.findFirstByUsuarioIdAndFechaFinGreaterThanEqualAndFechaLevantamientoIsNullOrderByFechaFinDesc(
                        idUsuario, LocalDate.now())
                .ifPresent(s -> {
                    throw new UsuarioSancionadoException("El usuario está suspendido hasta el "
                            + s.getFechaFin().format(FECHA) + ". Motivo: " + s.getMotivo());
                });
    }

    static SancionResponseDTO aDTO(Sancion s) {
        return new SancionResponseDTO(s.getId(), s.getUsuario().getId(), s.getUsuario().getNombreCompleto(),
                s.getUsuario().getDni(), s.getMotivo(), s.getFechaInicio(), s.getFechaFin(),
                s.getBibliotecario().getId(), s.getBibliotecario().getNombreCompleto(),
                s.estaActiva(LocalDate.now()) ? "ACTIVA" : "FINALIZADA", s.getFechaLevantamiento());
    }
}
