package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

/** RF07 / RF08 / RF15 - Devoluciones, cálculo de multa y pagos de multa. */
@Service
public class DevolucionService {

    /** Pago en efectivo: 15% de descuento sobre el monto que se salda. */
    static final BigDecimal DESCUENTO_EFECTIVO = new BigDecimal("0.15");

    private static final EnumSet<EstadoEjemplar> ESTADOS_AL_DEVOLVER =
            EnumSet.of(EstadoEjemplar.DISPONIBLE, EstadoEjemplar.DANADO, EstadoEjemplar.PERDIDO, EstadoEjemplar.BAJA);

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;
    private final PagoMultaRepository pagoMultaRepository;
    private final UsuarioService usuarioService;
    private final NotificacionService notificacionService;
    private final ReservaService reservaService;
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    public DevolucionService(PrestamoRepository prestamoRepository, DevolucionRepository devolucionRepository,
                             MultaRepository multaRepository, PagoMultaRepository pagoMultaRepository,
                             UsuarioService usuarioService,
                             NotificacionService notificacionService, ReservaService reservaService,
                             CajaService cajaService, AuditoriaService auditoriaService) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
        this.pagoMultaRepository = pagoMultaRepository;
        this.usuarioService = usuarioService;
        this.notificacionService = notificacionService;
        this.reservaService = reservaService;
        this.cajaService = cajaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * RF07 / RF08 - Registrar devolución y calcular multa.
     * - FECHA_FIJA con retraso: multa = días de retraso × tarifa guardada en el préstamo.
     * - ABIERTO: nunca genera multa.
     * - Si el libro vuelve bien, el ejemplar pasa al primero de la cola de reservas (FIFO) o queda DISPONIBLE.
     *   Si vuelve DANADO/PERDIDO, se marca así y no se asigna a nadie.
     * - Al devolver se COBRA el precio de la copia (los dos tipos de préstamo). La multa va aparte.
     */
    @Transactional
    public DevolucionResponseDTO registrarDevolucion(NuevaDevolucionDTO datos, Long idPersonal) {
        Usuario personal = usuarioService.buscarPersonal(idPersonal);
        Prestamo prestamo = prestamoRepository.findById(datos.idPrestamo())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el préstamo con id " + datos.idPrestamo()));
        if (prestamo.getEstado() == EstadoPrestamo.DEVUELTO || devolucionRepository.existsByPrestamoId(prestamo.getId())) {
            throw new OperacionInvalidaException("El préstamo " + prestamo.getId() + " ya fue devuelto");
        }
        EstadoEjemplar estadoFinal = datos.estadoEjemplar() != null ? datos.estadoEjemplar() : EstadoEjemplar.DISPONIBLE;
        if (!ESTADOS_AL_DEVOLVER.contains(estadoFinal)) {
            throw new OperacionInvalidaException("estado_ejemplar debe ser DISPONIBLE, DANADO, PERDIDO o BAJA");
        }
        // Si la copia tiene precio, el cobro necesita una caja abierta: si no hay, no se devuelve.
        if (prestamo.getPrecioCopia() != null) {
            cajaService.exigirCajaAbierta();
        }

        LocalDateTime ahora = LocalDateTime.now();
        int diasRetraso = prestamo.getTipo() == TipoPrestamo.ABIERTO ? 0
                : calcularDiasRetraso(prestamo.getFechaVencimiento(), ahora.toLocalDate());

        Devolucion d = new Devolucion();
        d.setPrestamo(prestamo);
        d.setFechaDevolucion(ahora);
        d.setDiasRetraso(diasRetraso);
        d.setObservaciones(datos.observaciones());
        d = devolucionRepository.save(d);
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);

        Multa multa = null;
        if (diasRetraso > 0) {
            // La tarifa SIEMPRE es la que se copió en el préstamo. Si falta, es dato
            // corrupto de una fila antigua: se falla en vez de usar la tarifa vigente,
            // que es justo lo que el diseño quiere evitar.
            if (prestamo.getTarifaMultaDiaria() == null) {
                throw new OperacionInvalidaException(
                        "El préstamo " + prestamo.getId() + " no tiene tarifa registrada; no se puede calcular la multa");
            }
            BigDecimal tarifa = prestamo.getTarifaMultaDiaria();
            multa = new Multa();
            multa.setDevolucion(d);
            multa.setUsuario(prestamo.getLector());
            multa.setMonto(tarifa.multiply(BigDecimal.valueOf(diasRetraso)));
            multa.setMontoPagado(BigDecimal.ZERO);
            multa.setEstado(EstadoMulta.PENDIENTE);
            multa.setFechaGeneracion(ahora);
            multa = multaRepository.save(multa);
            notificacionService.crear(prestamo.getLector(), TipoNotificacion.MULTA_PENDIENTE,
                    NotificacionService.mensajeMulta(multa) + " (" + diasRetraso + " día(s) de retraso)", multa.getId());
        }

        Ejemplar ejemplar = prestamo.getEjemplar();
        Long idReservaNotificada = null;
        if (estadoFinal == EstadoEjemplar.DISPONIBLE) {
            idReservaNotificada = reservaService.asignarEjemplarLiberado(ejemplar).map(Reserva::getId).orElse(null);
        } else {
            ejemplar.setEstado(estadoFinal);
        }

        // El precio de la copia se cobra al devolver (los dos tipos de préstamo).
        // La multa se cobra aparte, cuando el lector la pague.
        MetodoPago metodoCobro = datos.metodoPago() != null ? datos.metodoPago() : MetodoPago.EFECTIVO;
        CajaMovimientoDTO movimiento = cajaService.registrarCobroPrestamo(prestamo, d, metodoCobro, personal.getId());

        auditoriaService.registrar(idPersonal, TipoAccionAuditoria.REGISTRAR_DEVOLUCION, "DEVOLUCION", d.getId(),
                "Registró la devolución de '" + ejemplar.getLibro().getTitulo() + "' ("
                        + ejemplar.getCodigoInventario() + ") de " + prestamo.getLector().getNombreCompleto()
                        + (multa == null ? "" : " · multa de S/ " + multa.getMonto())
                        + (movimiento == null ? "" : " · cobró S/ " + movimiento.monto()));

        return new DevolucionResponseDTO(d.getId(), prestamo.getId(), d.getFechaDevolucion(), diasRetraso,
                d.getObservaciones(), multa == null ? null : multa.getId(),
                multa == null ? null : multa.getMonto(), idReservaNotificada,
                movimiento == null ? null : movimiento.monto(),
                movimiento == null ? null : movimiento.metodoPago(),
                movimiento == null ? null : movimiento.id());
    }

    /**
     * RF15 - Registrar pago de multa. Acepta pagos parciales:
     * el monto debe ser mayor que 0 y no mayor que el saldo. La multa queda PARCIAL o PAGADA.
     *
     * REGLA DE NEGOCIO: pagar en EFECTIVO da 15% de descuento. El monto que se digita
     * es la deuda que se quiere saldar; en efectivo la biblioteca solo cobra el 85%
     * y la diferencia queda registrada como descuento en el pago. En los demás métodos
     * se cobra el monto completo.
     * Ejemplo: saldo S/ 10.00 pagando en efectivo se cobra S/ 8.50 y se condona S/ 1.50.
     */
    @Transactional
    public PagoMultaResponseDTO registrarPagoMulta(Long idMulta, PagoMultaDTO datos, Long idBibliotecario) {
        Usuario bibliotecario = usuarioService.buscarPersonal(idBibliotecario);
        Multa multa = multaRepository.findByIdForUpdate(idMulta)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la multa con id " + idMulta));
        if (multa.getEstado() == EstadoMulta.PAGADA) {
            throw new OperacionInvalidaException("La multa " + idMulta + " ya fue pagada");
        }
        BigDecimal saldo = multa.getSaldo();
        if (datos.montoPagado().signum() <= 0) {
            throw new OperacionInvalidaException("El monto pagado debe ser mayor que 0");
        }
        if (datos.montoPagado().compareTo(saldo) > 0) {
            throw new OperacionInvalidaException("El monto pagado (S/ " + datos.montoPagado()
                    + ") supera el saldo de la multa (S/ " + saldo + ")");
        }
        // El dinero entra a la caja: tiene que haber una caja abierta.
        cajaService.exigirCajaAbierta();

        // Efectivo: 15% de descuento sobre el monto que se salda.
        boolean hayDescuento = datos.metodoPago() == MetodoPago.EFECTIVO;
        BigDecimal descuento = hayDescuento
                ? datos.montoPagado().multiply(DESCUENTO_EFECTIVO).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal efectivoRecibido = datos.montoPagado().subtract(descuento);
        BigDecimal deudaCubierta = datos.montoPagado();

        PagoMulta pago = new PagoMulta();
        pago.setMulta(multa);
        pago.setMontoPagado(efectivoRecibido);
        pago.setDescuento(descuento);
        pago.setMetodoPago(datos.metodoPago());
        pago.setFechaPago(LocalDateTime.now());
        pago.setBibliotecario(bibliotecario);
        pago = pagoMultaRepository.save(pago);

        // La deuda se reduce por el monto completo; el descuento ya viene incluido en el.
        multa.setMontoPagado(multa.getMontoPagado().add(deudaCubierta));
        multa.setEstado(multa.getSaldo().signum() == 0 ? EstadoMulta.PAGADA : EstadoMulta.PARCIAL);

        // A la caja entra el efectivo real recibido; el descuento queda registrado aparte.
        cajaService.registrarCobroMulta(pago, idBibliotecario);

        auditoriaService.registrar(idBibliotecario, TipoAccionAuditoria.COBRAR_MULTA, "MULTA", multa.getId(),
                "Cobró S/ " + pago.getMontoPagado() + " de multa a " + multa.getUsuario().getNombreCompleto()
                        + " (" + datos.metodoPago().name().toLowerCase()
                        + (descuento.signum() > 0 ? ", con S/ " + descuento + " de descuento" : "") + ")");
        return pagoADTO(pago);
    }

    /** Pantalla "Pagos de multa". filtro: PENDIENTES (por defecto: pendientes y parciales), PAGADAS o TODAS. */
    @Transactional(readOnly = true)
    public List<MultaResponseDTO> listarMultas(String filtro) {
        String f = filtro == null || filtro.isBlank() ? "PENDIENTES" : filtro.trim().toUpperCase();
        List<Multa> multas = switch (f) {
            case "PENDIENTES" -> multaRepository.findByEstadoInOrderByFechaGeneracionDesc(
                    EnumSet.of(EstadoMulta.PENDIENTE, EstadoMulta.PARCIAL));
            case "PAGADAS" -> multaRepository.findByEstadoInOrderByFechaGeneracionDesc(EnumSet.of(EstadoMulta.PAGADA));
            case "TODAS" -> multaRepository.findAllByOrderByFechaGeneracionDesc();
            default -> throw new OperacionInvalidaException("Filtro inválido: use PENDIENTES, PAGADAS o TODAS");
        };
        return multas.stream().map(this::multaADTO).toList();
    }

    /** "Mis multas" del lector logueado. */
    @Transactional(readOnly = true)
    public List<MultaResponseDTO> misMultas(Long idLector) {
        return multaRepository.findByUsuarioIdOrderByFechaGeneracionDesc(idLector).stream().map(this::multaADTO).toList();
    }

    /** Historial de pagos (auditoría del gerente). Fechas null = mes actual. */
    @Transactional(readOnly = true)
    public List<PagoMultaResponseDTO> listarPagos(LocalDate desde, LocalDate hasta) {
        LocalDate[] r = ReporteService.rango(desde, hasta);
        return pagoMultaRepository.findByFechaPagoBetweenOrderByFechaPagoDesc(r[0].atStartOfDay(), r[1].atTime(LocalTime.MAX))
                .stream().map(DevolucionService::pagoADTO).toList();
    }

    /** Días completos después de la fecha de vencimiento (0 si a tiempo o si no tiene fecha). */
    static int calcularDiasRetraso(LocalDate vencimiento, LocalDate devolucion) {
        if (vencimiento == null) return 0;
        long dias = ChronoUnit.DAYS.between(vencimiento, devolucion);
        return (int) Math.max(0, dias);
    }

    MultaResponseDTO multaADTO(Multa m) {
        List<PagoMulta> pagos = pagoMultaRepository.findByMultaIdOrderByFechaPagoAsc(m.getId());
        PagoMulta ultimo = pagos.isEmpty() ? null : pagos.get(pagos.size() - 1);
        Prestamo p = m.getDevolucion().getPrestamo();
        return new MultaResponseDTO(m.getId(), m.getUsuario().getId(), m.getUsuario().getNombreCompleto(),
                m.getUsuario().getDni(), p.getEjemplar().getLibro().getTitulo(), m.getDevolucion().getDiasRetraso(),
                m.getMonto(), m.getMontoPagado(), m.getSaldo(), m.getEstado().name(), m.getFechaGeneracion(),
                ultimo == null ? null : ultimo.getMetodoPago().name(), ultimo == null ? null : ultimo.getFechaPago());
    }

    static PagoMultaResponseDTO pagoADTO(PagoMulta p) {
        Multa m = p.getMulta();
        return new PagoMultaResponseDTO(p.getId(), m.getId(), m.getUsuario().getNombreCompleto(), p.getMontoPagado(),
                p.getDescuento(), p.getMetodoPago().name(), p.getFechaPago(), p.getBibliotecario().getId(), m.getSaldo(),
                m.getEstado().name());
    }
}
