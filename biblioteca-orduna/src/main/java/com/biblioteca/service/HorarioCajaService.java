package com.biblioteca.service;

import com.biblioteca.dto.GuardarHorariosCajaDTO;
import com.biblioteca.dto.HorarioCajaDTO;
import com.biblioteca.dto.HorarioCajaResponseDTO;
import com.biblioteca.dto.MiHorarioCajaDTO;
import com.biblioteca.dto.UsuarioResponseDTO;
import com.biblioteca.entity.HorarioCaja;
import com.biblioteca.entity.Rol;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.Usuario;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.repository.HorarioCajaRepository;
import com.biblioteca.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Horarios de la caja: qué días se abre, a qué hora, a qué hora se cierra y quién
 * es el encargado. Lo configura el gerente; el personal lo consulta desde la
 * pantalla Caja para saber a qué hora se cierra la caja sola.
 *
 * Un día = una fila (dia_semana es UNIQUE en la base). No se permiten dos filas
 * del mismo día ni una hora de fin menor o igual que la de inicio.
 */
@Service
public class HorarioCajaService {

    /** La misma zona del cron (TareasProgramadas), aunque el servidor esté en UTC. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final HorarioCajaRepository horarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public HorarioCajaService(HorarioCajaRepository horarioRepository, UsuarioRepository usuarioRepository,
                              UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.horarioRepository = horarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /** GET /api/configuracion-caja - todos los horarios, ordenados por día. */
    @Transactional(readOnly = true)
    public List<HorarioCajaResponseDTO> listar() {
        return horarioRepository.findAllByOrderByDiaSemanaAsc().stream().map(this::aDTO).toList();
    }

    /** GET /api/configuracion-caja/encargados - personal activo que puede atender la caja. */
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> encargados() {
        // Los mismos perfiles que acepta CajaService.encargadoValido(): el gerente
        // también puede ser encargado de una caja.
        List<Usuario> personal = new ArrayList<>(usuarioRepository.findByRolAndActivoTrue(Rol.BIBLIOTECARIO));
        personal.addAll(usuarioRepository.findByRolAndActivoTrue(Rol.GERENTE));
        return personal.stream()
                .sorted(Comparator.comparing(Usuario::getNombreCompleto))
                .map(usuarioService::aDTO).toList();
    }

    /** GET /api/configuracion-caja/mi-horario - los horarios del usuario y el de hoy. */
    @Transactional(readOnly = true)
    public MiHorarioCajaDTO miHorario(Long idUsuario) {
        List<HorarioCajaResponseDTO> mios = horarioRepository
                .findByEncargadoIdOrderByDiaSemanaAsc(idUsuario).stream().map(this::aDTO).toList();
        HorarioCaja hoy = horarioDeHoy();
        HorarioCajaResponseDTO deHoy = hoy != null && hoy.getEncargado() != null
                && hoy.getEncargado().getId().equals(idUsuario) ? aDTO(hoy) : null;
        return new MiHorarioCajaDTO(deHoy, mios);
    }

    /**
     * El horario de hoy, o null si hoy no hay caja programada. Lo usa también
     * CajaService para decidir si llega la hora de cerrar sola.
     */
    @Transactional(readOnly = true)
    public HorarioCaja horarioDeHoy() {
        return horarioRepository.findByDiaSemana(LocalDate.now(ZONA).getDayOfWeek()).orElse(null);
    }

    /**
     * PUT /api/configuracion-caja - reemplaza todos los horarios por los recibidos.
     * Enviar una lista vacía quita la configuración (la caja no se cierra sola).
     */
    @Transactional
    public List<HorarioCajaResponseDTO> guardar(GuardarHorariosCajaDTO datos, Long idActor) {
        List<HorarioCajaDTO> recibidos = datos.horarios() == null ? List.of() : datos.horarios();
        Set<DayOfWeek> dias = new HashSet<>();
        List<HorarioCaja> nuevos = new ArrayList<>();

        for (HorarioCajaDTO h : recibidos) {
            if (h.diaSemana() == null || h.horaInicio() == null || h.horaFin() == null) {
                throw new OperacionInvalidaException("Cada horario necesita día, hora de inicio y hora de fin");
            }
            if (!dias.add(h.diaSemana())) {
                throw new OperacionInvalidaException("El día " + nombreDia(h.diaSemana())
                        + " está repetido: solo puede haber un horario por día");
            }
            if (!h.horaFin().isAfter(h.horaInicio())) {
                throw new OperacionInvalidaException("En " + nombreDia(h.diaSemana())
                        + " la hora de fin debe ser mayor que la de inicio");
            }
            HorarioCaja horario = new HorarioCaja();
            horario.setDiaSemana(h.diaSemana());
            horario.setHoraInicio(h.horaInicio());
            horario.setHoraFin(h.horaFin());
            if (h.idEncargado() != null) {
                Usuario encargado = usuarioService.buscarPersonal(h.idEncargado());
                horario.setEncargado(encargado);
            }
            nuevos.add(horario);
        }

        // Borrado en bloque primero: dia_semana es UNIQUE y el UPDATE no debe
        // chocar con las filas que se están reemplazando.
        horarioRepository.deleteAllInBatch();
        horarioRepository.saveAll(nuevos);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.CONFIGURAR_CAJA, "CAJA", null,
                "Configuró " + nuevos.size() + " horario(s) de caja");
        return listar();
    }

    private HorarioCajaResponseDTO aDTO(HorarioCaja h) {
        Usuario u = h.getEncargado();
        return new HorarioCajaResponseDTO(h.getId(),
                h.getDiaSemana() == null ? null : h.getDiaSemana().name(), h.getHoraInicio(),
                h.getHoraFin(), u == null ? null : u.getId(),
                u == null ? null : u.getNombreCompleto());
    }

    private static String nombreDia(DayOfWeek dia) {
        if (dia == null) return "—";
        return switch (dia) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
