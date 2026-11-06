package com.biblioteca.service;

import com.biblioteca.dto.ColaReservasDTO;
import com.biblioteca.dto.NuevaReservaDTO;
import com.biblioteca.dto.ReservaMostradorDTO;
import com.biblioteca.dto.ReservaResponseDTO;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AccesoDenegadoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.exception.ReservaActivaDuplicadaException;
import com.biblioteca.repository.EjemplarRepository;
import com.biblioteca.repository.LibroRepository;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * RF09 - Reservas con cola FIFO.
 * Reglas del documento:
 * - Solo se puede reservar un libro que NO tenga ejemplares disponibles.
 * - Se atienden en estricto orden de llegada: al liberarse un ejemplar se aparta para el primero
 *   de la cola y se le notifica.
 * - La reserva vence si no recoge el ejemplar dentro del plazo (dias_retiro_reserva).
 */
@Service
public class ReservaService {

    private static final EnumSet<EstadoReserva> ACTIVAS = EnumSet.of(EstadoReserva.PENDIENTE, EstadoReserva.NOTIFICADA);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaRepository reservaRepository;
    private final LibroRepository libroRepository;
    private final EjemplarRepository ejemplarRepository;
    private final PrestamoRepository prestamoRepository;
    private final UsuarioService usuarioService;
    private final SancionService sancionService;
    private final NotificacionService notificacionService;
    private final ConfiguracionService configuracionService;

    public ReservaService(ReservaRepository reservaRepository, LibroRepository libroRepository,
                          EjemplarRepository ejemplarRepository, PrestamoRepository prestamoRepository,
                          UsuarioService usuarioService, SancionService sancionService,
                          NotificacionService notificacionService, ConfiguracionService configuracionService) {
        this.reservaRepository = reservaRepository;
        this.libroRepository = libroRepository;
        this.ejemplarRepository = ejemplarRepository;
        this.prestamoRepository = prestamoRepository;
        this.usuarioService = usuarioService;
        this.sancionService = sancionService;
        this.notificacionService = notificacionService;
        this.configuracionService = configuracionService;
    }

    /**
     * RF09 - Reservar libro (entra a la cola FIFO).
     * - UsuarioSancionadoException (403) si está sancionado.
     * - ReservaActivaDuplicadaException (400) si ya está en la cola de ese libro.
     * - OperacionInvalidaException (400) si hay ejemplares disponibles o ya tiene ese libro prestado.
     */
    @Transactional
    public ReservaResponseDTO crearReserva(NuevaReservaDTO datos, Long idUsuarioLector) {
        return reservar(datos.idLibro(), idUsuarioLector);
    }

    /**
     * RF09 - El personal anota a un lector en la cola (lo pidio en el mostrador o por telefono).
     * Mismas reglas que si reservara el propio lector. El actor (personal) sale del token y
     * el permiso lo pone SecurityConfig (/api/reservas/** solo bibliotecario o gerente).
     */
    @Transactional
    public ReservaResponseDTO crearReservaEnMostrador(ReservaMostradorDTO datos, Long idActor) {
        Usuario actor = usuarioService.buscar(idActor);
        if (!UsuarioService.esPersonal(actor)) {
            throw new AccesoDenegadoException("Solo el personal puede reservar a nombre de un lector");
        }
        return reservar(datos.idLibro(), datos.idUsuarioLector());
    }

    private ReservaResponseDTO reservar(Long idLibro, Long idUsuarioLector) {
        Usuario lector = usuarioService.buscarLector(idUsuarioLector);
        sancionService.verificarSinSancion(lector.getId());
        Libro libro = libroRepository.findById(idLibro)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el libro con id " + idLibro));
        if (libro.getTipoLibro() == TipoLibro.VIRTUAL) {
            throw new OperacionInvalidaException("'" + libro.getTitulo()
                    + "' es un libro virtual: no se reserva, se alquila o se compra desde el catálogo");
        }

        if (reservaRepository.existsByLibroIdAndLectorIdAndEstadoIn(libro.getId(), lector.getId(), ACTIVAS)) {
            throw new ReservaActivaDuplicadaException("Ya tiene una reserva activa para el libro '" + libro.getTitulo() + "'");
        }
        if (prestamoRepository.existsByLectorIdAndEjemplarLibroIdAndEstado(lector.getId(), libro.getId(), EstadoPrestamo.ACTIVO)) {
            throw new OperacionInvalidaException("Ya tiene prestado un ejemplar de '" + libro.getTitulo() + "'");
        }
        if (ejemplarRepository.countByLibroIdAndEstado(libro.getId(), EstadoEjemplar.DISPONIBLE) > 0) {
            throw new OperacionInvalidaException("'" + libro.getTitulo()
                    + "' tiene ejemplares disponibles: solicite el préstamo en mostrador en lugar de reservar");
        }

        Reserva r = new Reserva();
        r.setLibro(libro);
        r.setLector(lector);
        r.setFechaReserva(LocalDateTime.now());
        r.setEstado(EstadoReserva.PENDIENTE);
        return aDTO(reservaRepository.save(r));
    }

    /** Cancelar una reserva activa. El lector solo cancela las suyas; el personal, cualquiera. */
    @Transactional
    public ReservaResponseDTO cancelarReserva(Long idReserva, Long idActor) {
        Usuario actor = usuarioService.buscar(idActor);
        Reserva r = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la reserva " + idReserva));
        if (!UsuarioService.esPersonal(actor) && !r.getLector().getId().equals(actor.getId())) {
            throw new AccesoDenegadoException("Solo puede cancelar sus propias reservas");
        }
        if (!ACTIVAS.contains(r.getEstado())) {
            throw new OperacionInvalidaException("La reserva ya está " + r.getEstado().name().toLowerCase());
        }
        boolean teniaEjemplar = r.getEstado() == EstadoReserva.NOTIFICADA;
        r.setEstado(EstadoReserva.CANCELADA);
        if (teniaEjemplar && r.getEjemplarAsignado() != null) {
            asignarEjemplarLiberado(r.getEjemplarAsignado());   // pasa al siguiente de la cola
        }
        return aDTO(r);
    }

    /**
     * Se llama cada vez que un ejemplar queda libre (devolución, alta de ejemplar, reparación,
     * reserva cancelada o vencida). Si hay cola, lo aparta para el primero (RESERVADO) y le avisa;
     * si no, queda DISPONIBLE.
     */
    @Transactional
    public Optional<Reserva> asignarEjemplarLiberado(Ejemplar ejemplar) {
        Optional<Reserva> siguiente = reservaRepository.findFirstByLibroIdAndEstadoOrderByFechaReservaAscIdAsc(
                ejemplar.getLibro().getId(), EstadoReserva.PENDIENTE);
        if (siguiente.isEmpty()) {
            ejemplar.setEstado(EstadoEjemplar.DISPONIBLE);
            return Optional.empty();
        }
        Reserva r = siguiente.get();
        int plazo = configuracionService.obtenerParametros().getDiasRetiroReserva();
        r.setEstado(EstadoReserva.NOTIFICADA);
        r.setEjemplarAsignado(ejemplar);
        r.setFechaNotificacion(LocalDateTime.now());
        r.setFechaLimiteRetiro(LocalDate.now().plusDays(plazo));
        ejemplar.setEstado(EstadoEjemplar.RESERVADO);
        notificacionService.crear(r.getLector(), TipoNotificacion.RESERVA_DISPONIBLE,
                "El libro '" + ejemplar.getLibro().getTitulo() + "' que reservaste ya está disponible. "
                        + "Recógelo hasta el " + r.getFechaLimiteRetiro().format(FECHA)
                        + " (ejemplar " + ejemplar.getCodigoInventario() + ").", r.getId());
        return Optional.of(r);
    }

    /** Tarea diaria: vence las reservas NOTIFICADAS no recogidas y pasa el ejemplar al siguiente. */
    @Transactional
    public int vencerReservasNoRetiradas() {
        List<Reserva> vencidas = reservaRepository.findByEstadoAndFechaLimiteRetiroBefore(
                EstadoReserva.NOTIFICADA, LocalDate.now());
        for (Reserva r : vencidas) {
            r.setEstado(EstadoReserva.VENCIDA);
            notificacionService.crear(r.getLector(), TipoNotificacion.RESERVA_VENCIDA,
                    "Tu reserva de '" + r.getLibro().getTitulo() + "' venció porque no recogiste el libro a tiempo.",
                    r.getId());
            if (r.getEjemplarAsignado() != null) {
                asignarEjemplarLiberado(r.getEjemplarAsignado());
            }
        }
        return vencidas.size();
    }

    /** Pantalla Reservas (personal). estado null = todas, más recientes primero. */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarReservas(EstadoReserva estado) {
        List<Reserva> lista = estado == null
                ? reservaRepository.findAllByOrderByFechaReservaDesc()
                : reservaRepository.findByEstadoInOrderByFechaReservaAscIdAsc(List.of(estado));
        return lista.stream().map(this::aDTO).toList();
    }

    /** Panel "Cola de reservas" de un libro: primero el notificado, luego los que esperan en orden. */
    @Transactional(readOnly = true)
    public ColaReservasDTO colaDeLibro(Long idLibro) {
        Libro libro = libroRepository.findById(idLibro)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el libro con id " + idLibro));
        List<ReservaResponseDTO> cola = reservaRepository
                .findByLibroIdAndEstadoInOrderByFechaReservaAscIdAsc(idLibro, ACTIVAS).stream()
                .sorted((a, b) -> Boolean.compare(b.getEstado() == EstadoReserva.NOTIFICADA,
                        a.getEstado() == EstadoReserva.NOTIFICADA))
                .map(this::aDTO).toList();
        return new ColaReservasDTO(libro.getId(), libro.getTitulo(), cola);
    }

    /** Reservas del lector logueado. */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> misReservas(Long idLector) {
        return reservaRepository.findByLectorIdOrderByFechaReservaDesc(idLector).stream().map(this::aDTO).toList();
    }

    ReservaResponseDTO aDTO(Reserva r) {
        long posicion = r.getEstado() == EstadoReserva.PENDIENTE
                ? reservaRepository.countByLibroIdAndEstadoAndFechaReservaLessThanEqual(
                r.getLibro().getId(), EstadoReserva.PENDIENTE, r.getFechaReserva())
                : 0;
        return new ReservaResponseDTO(r.getId(), r.getLibro().getId(), r.getLibro().getTitulo(),
                r.getLector().getId(), r.getLector().getNombreCompleto(), r.getFechaReserva(),
                r.getEstado().name(), posicion,
                r.getEjemplarAsignado() == null ? null : r.getEjemplarAsignado().getCodigoInventario(),
                r.getFechaLimiteRetiro());
    }
}
