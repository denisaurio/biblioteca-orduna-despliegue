package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.MultaRepository;
import com.biblioteca.repository.NotificacionRepository;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.UsuarioRepository;
import com.biblioteca.realtime.RealtimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * RF11 - Avisos y notificaciones.
 * - Automáticos: vencimiento próximo, préstamo vencido, reserva disponible/vencida, multa, nuevo libro.
 *   Respetan los interruptores de "Configuración de avisos".
 * - Administrativos: los crea el bibliotecario o gerente con "+ Crear aviso".
 */
@Service
public class NotificacionService {

    /** Cada cuántos días se repite el recordatorio de una multa sin pagar. */
    public static final int DIAS_ENTRE_RECORDATORIOS_MULTA = 7;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final EnumSet<EstadoMulta> CON_DEUDA = EnumSet.of(EstadoMulta.PENDIENTE, EstadoMulta.PARCIAL);

    private final NotificacionRepository notificacionRepository;
    private final PrestamoRepository prestamoRepository;
    private final MultaRepository multaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;
    private final RealtimeService realtimeService;

    public NotificacionService(NotificacionRepository notificacionRepository, PrestamoRepository prestamoRepository,
                               MultaRepository multaRepository, UsuarioRepository usuarioRepository,
                               ConfiguracionService configuracionService, UsuarioService usuarioService,
                               AuditoriaService auditoriaService, RealtimeService realtimeService) {
        this.notificacionRepository = notificacionRepository;
        this.prestamoRepository = prestamoRepository;
        this.multaRepository = multaRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionService = configuracionService;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
        this.realtimeService = realtimeService;
    }

    /**
     * Crea un aviso automático si su tipo está activado en la configuración.
     * Devuelve vacío si el tipo está desactivado.
     */
    @Transactional
    public Optional<Notificacion> crear(Usuario destinatario, TipoNotificacion tipo, String mensaje, Long idReferencia) {
        if (!estaActivado(tipo)) {
            return Optional.empty();
        }
        return Optional.of(guardar(destinatario, tipo, mensaje, idReferencia, null));
    }

    /** RF11 - "+ Crear aviso". Si id_usuario es null, se envía a todos los lectores activos. */
    @Transactional
    public int crearAvisoAdministrativo(CrearAvisoDTO datos, Long idCreador) {
        Usuario creador = usuarioService.buscarPersonal(idCreador);
        String mensaje = datos.mensaje().trim();
        int destinatarios;
        if (datos.idUsuario() != null) {
            guardar(usuarioService.buscar(datos.idUsuario()), TipoNotificacion.ADMINISTRATIVO, mensaje, null, creador);
            destinatarios = 1;
        } else {
            List<Usuario> lectores = usuarioRepository.findByRolAndActivoTrue(Rol.LECTOR);
            lectores.forEach(l -> guardar(l, TipoNotificacion.ADMINISTRATIVO, mensaje, null, creador));
            destinatarios = lectores.size();
        }
        auditoriaService.registrar(idCreador, TipoAccionAuditoria.CREAR_AVISO, "AVISO", null,
                "Creó un aviso para " + destinatarios + " lector(es): " + mensaje);
        return destinatarios;
    }

    /**
     * Aviso para el personal (bibliotecarios y gerentes). Los avisos van a un usuario
     * concreto, asi que se crea uno por cada persona del personal: asi todas ven en su
     * campanita que hay un comprobante de Yape por revisar.
     */
    @Transactional
    public int crearParaPersonal(TipoNotificacion tipo, String mensaje, Long idReferencia) {
        List<Usuario> personal = new java.util.ArrayList<>();
        personal.addAll(usuarioRepository.findByRolAndActivoTrue(Rol.BIBLIOTECARIO));
        personal.addAll(usuarioRepository.findByRolAndActivoTrue(Rol.GERENTE));
        personal.forEach(p -> guardar(p, tipo, mensaje, idReferencia, null));
        return personal.size();
    }

    /** Aviso "nuevo libro en catálogo" a todos los lectores activos. */
    @Transactional
    public int notificarNuevoLibro(Libro libro) {
        if (!estaActivado(TipoNotificacion.NUEVO_LIBRO)) {
            return 0;
        }
        List<Usuario> lectores = usuarioRepository.findByRolAndActivoTrue(Rol.LECTOR);
        String msg = "Nuevo libro en el catálogo: '" + libro.getTitulo() + "'.";
        lectores.forEach(l -> guardar(l, TipoNotificacion.NUEVO_LIBRO, msg, libro.getId(), null));
        return lectores.size();
    }

    /**
     * RF11 - Avisos automáticos del día (vencimientos y multas). No repite un aviso ya enviado hoy
     * (o en los últimos 7 días para multas), así que se puede ejecutar varias veces sin duplicar.
     * Los préstamos ABIERTOS no tienen fecha, así que nunca generan estos avisos.
     */
    @Transactional
    public ResultadoAvisosDTO generarAvisosAutomaticos() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioHoy = hoy.atStartOfDay();
        int proximos = 0, vencidos = 0, multas = 0;

        if (estaActivado(TipoNotificacion.VENCIMIENTO_PROXIMO)) {
            for (Prestamo p : prestamoRepository.findByEstadoAndFechaVencimiento(EstadoPrestamo.ACTIVO, hoy.plusDays(1))) {
                if (crearSiNoExiste(p.getLector(), TipoNotificacion.VENCIMIENTO_PROXIMO, p.getId(), inicioHoy,
                        "Tu préstamo de '" + p.getEjemplar().getLibro().getTitulo() + "' vence mañana ("
                                + p.getFechaVencimiento().format(FECHA) + "). Devuélvelo a tiempo para evitar multas.")) {
                    proximos++;
                }
            }
            for (Prestamo p : prestamoRepository.findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, hoy)) {
                long dias = ChronoUnit.DAYS.between(p.getFechaVencimiento(), hoy);
                if (crearSiNoExiste(p.getLector(), TipoNotificacion.PRESTAMO_VENCIDO, p.getId(), inicioHoy,
                        "Tu préstamo de '" + p.getEjemplar().getLibro().getTitulo() + "' venció hace " + dias
                                + " día(s). Se está generando una multa por cada día de retraso.")) {
                    vencidos++;
                }
            }
        }

        if (estaActivado(TipoNotificacion.MULTA_PENDIENTE)) {
            LocalDateTime ventanaMultas = inicioHoy.minusDays(DIAS_ENTRE_RECORDATORIOS_MULTA - 1L);
            for (Multa m : multaRepository.findByEstadoIn(CON_DEUDA)) {
                if (crearSiNoExiste(m.getUsuario(), TipoNotificacion.MULTA_PENDIENTE, m.getId(), ventanaMultas,
                        mensajeMulta(m))) {
                    multas++;
                }
            }
        }
        return new ResultadoAvisosDTO(proximos, vencidos, multas, proximos + vencidos + multas);
    }

    /** GET /api/notificaciones - avisos del usuario logueado (más recientes primero). */
    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> listarNotificaciones(Long idUsuario, boolean soloNoLeidas) {
        List<Notificacion> lista = soloNoLeidas
                ? notificacionRepository.findByUsuarioIdAndLeidaFalseOrderByFechaCreacionDescIdDesc(idUsuario)
                : notificacionRepository.findByUsuarioIdOrderByFechaCreacionDescIdDesc(idUsuario);
        return lista.stream().map(n -> aDTO(n, false)).toList();
    }

    /** Vista del personal: todos los avisos del sistema (filtro opcional por tipo), paginado. */
    @Transactional(readOnly = true)
    public PaginaDTO<NotificacionResponseDTO> listarTodas(TipoNotificacion tipo, Integer pagina, Integer tamanio) {
        var pageable = Paginas.de(pagina, tamanio);
        var page = tipo == null
                ? notificacionRepository.findAllByOrderByFechaCreacionDescIdDesc(pageable)
                : notificacionRepository.findByTipoInOrderByFechaCreacionDescIdDesc(List.of(tipo), pageable);
        return Paginas.aDTO(page, n -> aDTO(n, true));
    }

    /** Para el contador de la campanita. */
    @Transactional(readOnly = true)
    public long contarNoLeidas(Long idUsuario) {
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(idUsuario);
    }

    /** PATCH /api/notificaciones/{id}/leida - solo el dueño puede marcarla. */
    @Transactional
    public NotificacionResponseDTO marcarComoLeida(Long idNotificacion, Long idUsuario) {
        Notificacion n = notificacionRepository.findById(idNotificacion)
                .filter(x -> x.getUsuario().getId().equals(idUsuario))
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la notificación " + idNotificacion));
        marcar(n);
        realtimeService.publicarNotificacionesActualizadas(idUsuario);
        return aDTO(n, false);
    }

    /** PATCH /api/notificaciones/leidas - marca todas las del usuario. Devuelve cuántas cambió. */
    @Transactional
    public int marcarTodasComoLeidas(Long idUsuario) {
        List<Notificacion> pendientes =
                notificacionRepository.findByUsuarioIdAndLeidaFalseOrderByFechaCreacionDescIdDesc(idUsuario);
        pendientes.forEach(NotificacionService::marcar);
        realtimeService.publicarNotificacionesActualizadas(idUsuario);
        return pendientes.size();
    }

    static String mensajeMulta(Multa m) {
        return "Tienes una multa pendiente de S/ " + m.getSaldo()
                + ". No podrás realizar nuevos préstamos hasta pagarla.";
    }

    // ---------------- internos ----------------

    private boolean estaActivado(TipoNotificacion tipo) {
        Configuracion c = configuracionService.obtenerGeneral();
        return switch (tipo) {
            case VENCIMIENTO_PROXIMO, PRESTAMO_VENCIDO -> c.isAvisoVencimiento();
            case RESERVA_DISPONIBLE, RESERVA_VENCIDA -> c.isAvisoReserva();
            case MULTA_PENDIENTE -> c.isAvisoMulta();
            case NUEVO_LIBRO -> c.isAvisoNuevoLibro();
            case ACCESO_VIRTUAL, COMPROBANTE_YAPE, PAGO_RECHAZADO, ADMINISTRATIVO -> true;
        };
    }

    private Notificacion guardar(Usuario u, TipoNotificacion tipo, String mensaje, Long idRef, Usuario creador) {
        Notificacion n = new Notificacion();
        n.setUsuario(u);
        n.setTipo(tipo);
        n.setMensaje(mensaje);
        n.setIdReferencia(idRef);
        n.setFechaCreacion(LocalDateTime.now());
        n.setLeida(false);
        n.setCreadoPor(creador);
        Notificacion guardada = notificacionRepository.save(n);
        // Al navegador del destinatario le llega la campanita al instante (SSE),
        // después del commit para no anunciar algo que luego se revierta.
        realtimeService.publicarNotificacion(u.getId(), guardada.getId(), tipo.name(),
                mensaje, idRef, guardada.getFechaCreacion());
        return guardada;
    }

    private boolean crearSiNoExiste(Usuario u, TipoNotificacion tipo, Long idRef, LocalDateTime desde, String mensaje) {
        if (notificacionRepository.existsByUsuarioIdAndTipoAndIdReferenciaAndFechaCreacionGreaterThanEqual(
                u.getId(), tipo, idRef, desde)) {
            return false;
        }
        guardar(u, tipo, mensaje, idRef, null);
        return true;
    }

    private static void marcar(Notificacion n) {
        if (!n.isLeida()) {
            n.setLeida(true);
            n.setFechaLectura(LocalDateTime.now());
        }
    }

    static NotificacionResponseDTO aDTO(Notificacion n, boolean conDestinatario) {
        return new NotificacionResponseDTO(n.getId(), n.getTipo().name(), n.getMensaje(), n.getIdReferencia(),
                n.getFechaCreacion(), n.isLeida(),
                conDestinatario ? n.getUsuario().getNombreCompleto() : null,
                n.getCreadoPor() == null ? null : n.getCreadoPor().getNombreCompleto());
    }
}
