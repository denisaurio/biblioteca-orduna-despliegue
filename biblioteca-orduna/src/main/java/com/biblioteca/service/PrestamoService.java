package com.biblioteca.service;

import com.biblioteca.dto.AnularPrestamoDTO;
import com.biblioteca.dto.CajaMovimientoDTO;
import com.biblioteca.dto.CobrarPrestamoDTO;
import com.biblioteca.dto.NuevoPrestamoDTO;
import com.biblioteca.dto.PrestamoResponseDTO;
import com.biblioteca.entity.*;
import com.biblioteca.exception.EjemplarNoDisponibleException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.EjemplarRepository;
import com.biblioteca.repository.MultaRepository;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

/** RF06 - Préstamos (asistente Usuario → Libro → Confirmar). */
@Service
public class PrestamoService {

    /** Tope de días de un préstamo personalizado. */
    static final int DIAS_MAX_PERSONALIZADO = 365;

    private final PrestamoRepository prestamoRepository;
    private final EjemplarRepository ejemplarRepository;
    private final ReservaRepository reservaRepository;
    private final MultaRepository multaRepository;
    private final UsuarioService usuarioService;
    private final SancionService sancionService;
    private final ConfiguracionService configuracionService;
    private final AuditoriaService auditoriaService;
    private final CajaService cajaService;
    private final ReservaService reservaService;

    public PrestamoService(PrestamoRepository prestamoRepository, EjemplarRepository ejemplarRepository,
                           ReservaRepository reservaRepository, MultaRepository multaRepository,
                           UsuarioService usuarioService, SancionService sancionService,
                           ConfiguracionService configuracionService, AuditoriaService auditoriaService,
                           CajaService cajaService, ReservaService reservaService) {
        this.prestamoRepository = prestamoRepository;
        this.ejemplarRepository = ejemplarRepository;
        this.reservaRepository = reservaRepository;
        this.multaRepository = multaRepository;
        this.usuarioService = usuarioService;
        this.sancionService = sancionService;
        this.configuracionService = configuracionService;
        this.auditoriaService = auditoriaService;
        this.cajaService = cajaService;
        this.reservaService = reservaService;
    }

    /**
     * RF06 - Registrar préstamo.
     * - Con id_libro el sistema elige el ejemplar: el apartado para este lector si tenía reserva,
     *   o el primero disponible.
     * - FECHA_FIJA (fijo): vence en dias_max días. PERSONALIZADO: vence en los días que elige el personal.
     * - Los dos generan multa: se copia la tarifa vigente o la personalizada que mande el personal.
     * - ABIERTO ya no se puede crear (400).
     * - Hace falta una caja abierta: el préstamo guarda la caja donde empieza.
     * - El precio de la copia se cobra AL PRESTAR. Si presta el encargado de la caja (o el
     *   gerente), se cobra en el acto y el libro sale (ACTIVO). Si presta otro bibliotecario,
     *   queda POR_COBRAR: el ejemplar se aparta y el encargado lo cobra en la sección Caja.
     * Errores: UsuarioSancionadoException (403), EjemplarNoDisponibleException (400),
     * OperacionInvalidaException (400) si tiene multas sin pagar.
     */
    @Transactional
    public PrestamoResponseDTO registrarPrestamo(NuevoPrestamoDTO datos, Long idBibliotecario) {
        Usuario bibliotecario = usuarioService.buscarPersonal(idBibliotecario);
        Usuario lector = usuarioService.buscarLector(datos.idUsuarioLector());
        Caja caja = cajaService.cajaAbiertaParaOperar();

        sancionService.verificarSinSancion(lector.getId());
        if (multaRepository.existsByUsuarioIdAndEstadoIn(lector.getId(),
                EnumSet.of(EstadoMulta.PENDIENTE, EstadoMulta.PARCIAL))) {
            throw new OperacionInvalidaException("El lector tiene multas pendientes de pago");
        }

        Ejemplar ejemplar = elegirEjemplar(datos, lector);
        Long idLibro = ejemplar.getLibro().getId();

        switch (ejemplar.getEstado()) {
            case DISPONIBLE -> {
                // Si el lector estaba en la cola de este libro, su reserva queda atendida.
                reservaRepository.findFirstByLibroIdAndLectorIdAndEstado(idLibro, lector.getId(), EstadoReserva.PENDIENTE)
                        .ifPresent(r -> r.setEstado(EstadoReserva.ATENDIDA));
            }
            case RESERVADO -> {
                // Apartado por la cola FIFO: solo se lo puede llevar el lector notificado.
                Reserva r = reservaRepository.findFirstByEjemplarAsignadoIdAndEstado(ejemplar.getId(), EstadoReserva.NOTIFICADA)
                        .orElseThrow(() -> new EjemplarNoDisponibleException("El ejemplar está reservado"));
                if (!r.getLector().getId().equals(lector.getId())) {
                    throw new EjemplarNoDisponibleException(
                            "El ejemplar " + ejemplar.getCodigoInventario() + " está apartado para otro lector");
                }
                r.setEstado(EstadoReserva.ATENDIDA);
            }
            case PRESTADO -> throw new EjemplarNoDisponibleException(
                    "El ejemplar " + ejemplar.getCodigoInventario() + " ya está prestado");
            case DANADO, PERDIDO, BAJA -> throw new EjemplarNoDisponibleException(
                    "El ejemplar " + ejemplar.getCodigoInventario() + " no está en circulación ("
                            + ejemplar.getEstado().name().toLowerCase() + ")");
        }

        TipoPrestamo tipo = datos.tipoPrestamo() != null ? datos.tipoPrestamo() : TipoPrestamo.FECHA_FIJA;
        ConfiguracionMulta params = configuracionService.obtenerParametros();
        int dias = diasDelPrestamo(tipo, datos.diasPrestamo(), params);
        boolean tarifaPersonalizada = datos.tarifaMultaDiaria() != null;
        BigDecimal tarifa = (tarifaPersonalizada ? datos.tarifaMultaDiaria() : params.getTarifaMultaDiaria())
                .setScale(2, RoundingMode.HALF_UP);

        Prestamo p = new Prestamo();
        p.setEjemplar(ejemplar);
        p.setLector(lector);
        p.setBibliotecario(bibliotecario);
        p.setTipo(tipo);
        p.setFechaPrestamo(LocalDateTime.now());
        // El precio del ejemplar se COPIA al préstamo (como la tarifa de multa).
        p.setPrecioCopia(EjemplarService.precioDe(ejemplar.getPrecio(), ejemplar.getLibro()));
        // Fijo y personalizado tienen fecha de devolución y los dos generan multa.
        // La tarifa (de la configuración o personalizada) se COPIA en el préstamo:
        // cambiar la configuración después no altera este préstamo.
        p.setDiasPrestamo(dias);
        p.setFechaVencimiento(LocalDate.now().plusDays(dias));
        p.setTarifaMultaDiaria(tarifa);
        p.setTarifaPersonalizada(tarifaPersonalizada);
        // Se cobra al prestar. Si quien presta no puede cobrar (no es el encargado de la caja
        // ni el gerente), el préstamo queda POR_COBRAR y el ejemplar apartado (PRESTADO).
        boolean hayPrecio = p.getPrecioCopia() != null && p.getPrecioCopia().signum() > 0;
        boolean cobraAhora = CajaService.puedeCobrar(caja, bibliotecario);
        p.setCobroAlPrestar(true);
        p.setCajaInicio(caja);
        p.setEstado(hayPrecio && !cobraAhora ? EstadoPrestamo.POR_COBRAR : EstadoPrestamo.ACTIVO);
        ejemplar.setEstado(EstadoEjemplar.PRESTADO);
        p = prestamoRepository.save(p);
        CajaMovimientoDTO cobro = null;
        if (hayPrecio && cobraAhora) {
            MetodoPago metodo = datos.metodoPago() != null ? datos.metodoPago() : MetodoPago.EFECTIVO;
            cobro = cajaService.registrarCobroPrestamo(p, null, metodo, idBibliotecario);
        }
        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.REGISTRAR_PRESTAMO, "PRESTAMO", p.getId(),
                "Prestó '" + ejemplar.getLibro().getTitulo() + "' (" + ejemplar.getCodigoInventario()
                        + ") a " + lector.getNombreCompleto() + " (" + nombreTipo(tipo) + ", " + dias
                        + (dias == 1 ? " día" : " días") + ", multa S/ " + tarifa + " por día"
                        + (tarifaPersonalizada ? " personalizada" : "") + ") en la caja #" + caja.getId()
                        + (cobro != null ? " · cobró S/ " + cobro.monto() + " (" + cobro.metodoPago().toLowerCase() + ")"
                            : hayPrecio ? " · queda por cobrar (S/ " + p.getPrecioCopia() + ")" : ""));
        return aDTO(p, cobro == null ? null : caja.getId(), null);
    }

    /**
     * El encargado de la caja (o el gerente) cobra un préstamo POR_COBRAR. Recién ahora el
     * libro sale: la fecha del préstamo y la de devolución se cuentan desde este momento,
     * y la caja de inicio es la caja donde se cobró.
     */
    @Transactional
    public PrestamoResponseDTO cobrarPrestamo(Long idPrestamo, CobrarPrestamoDTO datos, Long idActor) {
        cajaService.exigirPuedeCobrar(idActor);
        Prestamo p = prestamoRepository.findByIdForUpdate(idPrestamo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el préstamo con id " + idPrestamo));
        if (p.getEstado() != EstadoPrestamo.POR_COBRAR) {
            throw new OperacionInvalidaException("El préstamo " + idPrestamo + " no está pendiente de cobro");
        }
        Caja caja = cajaService.cajaAbiertaParaOperar();
        int dias = p.getDiasPrestamo() != null ? p.getDiasPrestamo()
                : configuracionService.obtenerParametros().getDiasMax();
        p.setFechaPrestamo(LocalDateTime.now());
        p.setFechaVencimiento(LocalDate.now().plusDays(dias));
        p.setCajaInicio(caja);
        p.setEstado(EstadoPrestamo.ACTIVO);
        CajaMovimientoDTO cobro = cajaService.registrarCobroPrestamo(p, null, datos.metodoPago(), idActor);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.COBRAR_PRESTAMO, "PRESTAMO", p.getId(),
                "Cobró el préstamo de '" + p.getEjemplar().getLibro().getTitulo() + "' a "
                        + p.getLector().getNombreCompleto() + " en la caja #" + caja.getId()
                        + (cobro == null ? "" : ": S/ " + cobro.monto() + " (" + cobro.metodoPago().toLowerCase() + ")"));
        return aDTO(p, cobro == null ? null : caja.getId(), null);
    }

    /**
     * El cliente no pagó: el préstamo POR_COBRAR se anula y el ejemplar se libera (pasa al
     * primero de la cola de reservas o queda DISPONIBLE). Cualquier miembro del personal puede.
     */
    @Transactional
    public PrestamoResponseDTO anularPrestamo(Long idPrestamo, AnularPrestamoDTO datos, Long idActor) {
        usuarioService.buscarPersonal(idActor);
        Prestamo p = prestamoRepository.findByIdForUpdate(idPrestamo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el préstamo con id " + idPrestamo));
        if (p.getEstado() != EstadoPrestamo.POR_COBRAR) {
            throw new OperacionInvalidaException("Solo se anula un préstamo que todavía no se cobró");
        }
        p.setEstado(EstadoPrestamo.ANULADO);
        Ejemplar e = ejemplarRepository.findByIdForUpdate(p.getEjemplar().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el ejemplar del préstamo"));
        reservaService.asignarEjemplarLiberado(e);
        String motivo = datos == null || datos.motivo() == null || datos.motivo().isBlank() ? null : datos.motivo().trim();
        auditoriaService.registrar(idActor, TipoAccionAuditoria.ANULAR_PRESTAMO, "PRESTAMO", p.getId(),
                "Anuló el préstamo de '" + e.getLibro().getTitulo() + "' a " + p.getLector().getNombreCompleto()
                        + " (no se cobró)" + (motivo == null ? "" : ": " + motivo));
        return aDTO(p);
    }

    /**
     * Días del préstamo. FIJO: los dias_max de la configuración. PERSONALIZADO: los que elige el
     * personal (1 a 365). ABIERTO ya no se puede crear: queda solo para préstamos antiguos.
     */
    private static int diasDelPrestamo(TipoPrestamo tipo, Integer diasPedidos, ConfiguracionMulta params) {
        return switch (tipo) {
            case FECHA_FIJA -> params.getDiasMax();
            case PERSONALIZADO -> {
                if (diasPedidos == null || diasPedidos < 1 || diasPedidos > DIAS_MAX_PERSONALIZADO) {
                    throw new OperacionInvalidaException(
                            "Indique cuántos días dura el préstamo personalizado (de 1 a " + DIAS_MAX_PERSONALIZADO + ")");
                }
                yield diasPedidos;
            }
            case ABIERTO -> throw new OperacionInvalidaException(
                    "Los préstamos abiertos ya no existen: elija un préstamo fijo o personalizado");
        };
    }

    /** "fijo", "personalizado" o "abierto" (préstamos antiguos). */
    static String nombreTipo(TipoPrestamo tipo) {
        return switch (tipo) {
            case FECHA_FIJA -> "fijo";
            case PERSONALIZADO -> "personalizado";
            case ABIERTO -> "abierto";
        };
    }

    /**
     * Pantalla Devoluciones / listado de préstamos.
     * filtro: ACTIVOS (por defecto, incluye vencidos), VENCIDOS, DEVUELTOS, POR_COBRAR o TODOS.
     * q: busca por nombre o DNI del lector, título o código de ejemplar.
     */
    @Transactional(readOnly = true)
    public List<PrestamoResponseDTO> listarPrestamos(String filtro, String q) {
        String f = filtro == null || filtro.isBlank() ? "ACTIVOS" : filtro.trim().toUpperCase();
        LocalDate hoy = LocalDate.now();
        Stream<Prestamo> base = switch (f) {
            case "ACTIVOS" -> prestamoRepository.findByEstadoOrderByFechaVencimientoAsc(EstadoPrestamo.ACTIVO).stream();
            case "VENCIDOS" -> prestamoRepository.findByEstadoOrderByFechaVencimientoAsc(EstadoPrestamo.ACTIVO).stream()
                    .filter(p -> p.estaVencido(hoy));
            case "DEVUELTOS" -> prestamoRepository.findByEstadoOrderByFechaVencimientoAsc(EstadoPrestamo.DEVUELTO).stream();
            case "POR_COBRAR" -> prestamoRepository.findByEstadoOrderByFechaPrestamoAsc(EstadoPrestamo.POR_COBRAR).stream();
            case "TODOS" -> prestamoRepository.findAll().stream()
                    .sorted(Comparator.comparing(Prestamo::getFechaPrestamo).reversed());
            default -> throw new OperacionInvalidaException(
                    "Filtro inválido: use ACTIVOS, VENCIDOS, DEVUELTOS, POR_COBRAR o TODOS");
        };
        String texto = q == null ? "" : q.trim().toLowerCase();
        return base.filter(p -> texto.isEmpty() || coincide(p, texto)).map(PrestamoService::aDTO).toList();
    }

    /** "Mis préstamos" del lector logueado. */
    @Transactional(readOnly = true)
    public List<PrestamoResponseDTO> misPrestamos(Long idLector) {
        return prestamoRepository.findByLectorIdOrderByFechaPrestamoDesc(idLector).stream()
                .map(PrestamoService::aDTO).toList();
    }

    private Ejemplar elegirEjemplar(NuevoPrestamoDTO datos, Usuario lector) {
        if (datos.idEjemplar() != null) {
            Ejemplar e = ejemplarRepository.findByIdForUpdate(datos.idEjemplar())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el ejemplar con id " + datos.idEjemplar()));
            // Si también llega id_libro, el ejemplar tiene que ser de ese libro.
            if (datos.idLibro() != null && !e.getLibro().getId().equals(datos.idLibro())) {
                throw new OperacionInvalidaException("El ejemplar " + e.getCodigoInventario()
                        + " no pertenece al libro indicado");
            }
            return e;
        }
        if (datos.idLibro() == null) {
            throw new OperacionInvalidaException("Debe indicar id_ejemplar o id_libro");
        }
        // 1) ¿Tiene un ejemplar apartado por reserva?
        var apartado = reservaRepository.findFirstByLibroIdAndLectorIdAndEstado(
                datos.idLibro(), lector.getId(), EstadoReserva.NOTIFICADA);
        if (apartado.isPresent() && apartado.get().getEjemplarAsignado() != null) {
            return ejemplarRepository.findByIdForUpdate(apartado.get().getEjemplarAsignado().getId())
                    .orElseThrow(() -> new OperacionInvalidaException("El ejemplar apartado ya no está disponible"));
        }
        // 2) El primer ejemplar disponible (la consulta lleva bloqueo pesimista)
        return ejemplarRepository.findFirstByLibroIdAndEstadoOrderByIdAsc(datos.idLibro(), EstadoEjemplar.DISPONIBLE)
                .orElseThrow(() -> new EjemplarNoDisponibleException(
                        "No hay ejemplares disponibles de este libro. El lector puede reservarlo"));
    }

    private static boolean coincide(Prestamo p, String t) {
        return p.getLector().getNombreCompleto().toLowerCase().contains(t)
                || p.getLector().getDni().contains(t)
                || p.getEjemplar().getLibro().getTitulo().toLowerCase().contains(t)
                || p.getEjemplar().getCodigoInventario().toLowerCase().contains(t);
    }

    static PrestamoResponseDTO aDTO(Prestamo p) {
        return aDTO(p, null, null);
    }

    /**
     * idCajaCobro: caja donde se pagó el precio; fechaDevolucion: cuándo volvió el libro.
     * Los dos son opcionales (los llena CajaService.prestamosConCajas en bloque).
     */
    static PrestamoResponseDTO aDTO(Prestamo p, Long idCajaCobro, LocalDateTime fechaDevolucion) {
        LocalDate hoy = LocalDate.now();
        Ejemplar e = p.getEjemplar();
        boolean vencido = p.estaVencido(hoy);
        String estado = switch (p.getEstado()) {
            case DEVUELTO -> "DEVUELTO";
            case POR_COBRAR -> "POR_COBRAR";
            case ANULADO -> "ANULADO";
            case ACTIVO -> vencido ? "VENCIDO" : "ACTIVO";
        };
        int dias = p.getEstado() == EstadoPrestamo.ACTIVO
                ? DevolucionService.calcularDiasRetraso(p.getFechaVencimiento(), hoy) : 0;
        // Multa que lleva acumulada hoy si sigue sin devolver (se genera de verdad al devolver).
        BigDecimal multaAcumulada = p.getTarifaMultaDiaria() == null || dias == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : p.getTarifaMultaDiaria().multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP);
        Integer diasPrestamo = p.getDiasPrestamo();
        if (diasPrestamo == null && p.getFechaVencimiento() != null) {
            // Préstamos anteriores a la columna dias_prestamo: se deduce de las fechas.
            diasPrestamo = (int) ChronoUnit.DAYS.between(p.getFechaPrestamo().toLocalDate(), p.getFechaVencimiento());
        }
        return new PrestamoResponseDTO(p.getId(), e.getId(), e.getCodigoInventario(), e.getLibro().getId(),
                e.getLibro().getTitulo(), p.getLector().getId(), p.getLector().getNombreCompleto(),
                p.getLector().getDni(), p.getBibliotecario().getId(), p.getTipo().name(),
                p.getFechaPrestamo(), p.getFechaVencimiento(), dias, !vencido, estado, p.getPrecioCopia(),
                diasPrestamo, p.getTarifaMultaDiaria(), p.isTarifaPersonalizada(), multaAcumulada,
                p.getCajaInicio() == null ? null : p.getCajaInicio().getId(),
                p.getCajaFin() == null ? null : p.getCajaFin().getId(),
                p.isCobroAlPrestar(), idCajaCobro, fechaDevolucion,
                p.getBibliotecario().getNombreCompleto());
    }
}
