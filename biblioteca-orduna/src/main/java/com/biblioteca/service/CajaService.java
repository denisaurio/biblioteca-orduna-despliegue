package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * La caja: el cajón de dinero de la biblioteca.
 *
 * Es UNA SOLA para toda la biblioteca: solo puede haber una abierta a la vez y el
 * personal se turna. Lo garantiza la base de datos (columna "caja_abierta" con
 * índice UNIQUE) y no solo el código.
 *
 * El MOVIMIENTO es la única fuente de verdad del dinero: el total del día es la
 * suma de los movimientos del día, así que no hay dos cifras que se puedan
 * desajustar. Por eso ningún cobro se registra por otro lado: todos entran por
 * aquí (registrarCobroPrestamo, registrarCobroMulta, registrarCobroVirtual).
 *
 * Y no se cobra sin caja abierta: si el dinero entra al cajón fuera de una caja,
 * el arqueo del final del turno no cuadra.
 *
 * La caja NO es el turno (la bitácora del personal) ni el cierre diario (la foto
 * del día): son tres cosas distintas que conviven.
 */
@Service
public class CajaService {

    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /** Valor de la columna "caja_abierta" mientras la caja está abierta. */
    private static final Integer MARCA_ABIERTA = 1;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CajaRepository cajaRepository;
    private final CajaMovimientoRepository movimientoRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final PrestamoRepository prestamoRepository;
    private final ReservaRepository reservaRepository;
    private final AccesoVirtualRepository accesoRepository;
    private final MultaRepository multaRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public CajaService(CajaRepository cajaRepository, CajaMovimientoRepository movimientoRepository,
                       ComprobantePagoRepository comprobanteRepository, PrestamoRepository prestamoRepository,
                       ReservaRepository reservaRepository, AccesoVirtualRepository accesoRepository,
                       MultaRepository multaRepository, UsuarioService usuarioService,
                       AuditoriaService auditoriaService) {
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.prestamoRepository = prestamoRepository;
        this.reservaRepository = reservaRepository;
        this.accesoRepository = accesoRepository;
        this.multaRepository = multaRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    // ---------------- abrir y cerrar ----------------

    /** La caja abierta ahora (null si no hay ninguna). */
    @Transactional(readOnly = true)
    public CajaResponseDTO cajaAbierta() {
        return cajaRepository.findByCajaAbierta(MARCA_ABIERTA).map(this::aDTO).orElse(null);
    }

    /**
     * Abrir el cajón. Se registra el monto inicial (el dinero que ya había dentro).
     * Si ya hay una caja abierta, se dice quién la abrió y cuándo.
     */
    @Transactional
    public CajaResponseDTO abrir(AbrirCajaDTO datos, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        List<Caja> abiertas = cajaRepository.findByEstadoOrderByFechaAperturaDescForUpdate(EstadoCaja.ABIERTA);
        if (!abiertas.isEmpty()) {
            Caja abierta = abiertas.get(0);
            throw new OperacionInvalidaException("Ya hay una caja abierta desde el "
                    + abierta.getFechaApertura().format(FECHA_HORA) + " (la abrió "
                    + abierta.getUsuarioApertura().getNombreCompleto() + "). Ciérrala antes de abrir otra");
        }

        Caja caja = new Caja();
        caja.setFechaApertura(LocalDateTime.now());
        caja.setUsuarioApertura(actor);
        caja.setMontoInicial(dosDecimales(datos.montoInicial()));
        caja.setEstado(EstadoCaja.ABIERTA);
        caja.setCajaAbierta(MARCA_ABIERTA);
        caja.setObservaciones(recortar(datos.observaciones()));
        try {
            // saveAndFlush: si la base ya tiene una caja abierta (índice UNIQUE),
            // el error salta aquí y no al final de la transacción.
            caja = cajaRepository.saveAndFlush(caja);
        } catch (DataIntegrityViolationException e) {
            throw new OperacionInvalidaException(
                    "Ya hay una caja abierta. Ciérrala antes de abrir otra");
        }
        auditoriaService.registrar(idActor, TipoAccionAuditoria.ABRIR_CAJA, "CAJA", caja.getId(),
                "Abrió la caja con S/ " + caja.getMontoInicial() + " de monto inicial");
        return aDTO(caja);
    }

    /**
     * Cerrar el cajón: se cuenta el efectivo y se compara con lo que debería haber.
     * La diferencia es el sobrante (positivo) o el faltante (negativo).
     */
    @Transactional
    public CajaResponseDTO cerrar(Long idCaja, CerrarCajaDTO datos, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        Caja caja = cajaRepository.findByIdForUpdate(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new OperacionInvalidaException("Esa caja ya está cerrada");
        }
        BigDecimal efectivo = movimientoRepository.sumarEfectivoPorCaja(idCaja);
        BigDecimal esperado = caja.getMontoInicial().add(efectivo);
        BigDecimal contado = dosDecimales(datos.montoFinalDeclarado());

        caja.setFechaCierre(LocalDateTime.now());
        caja.setUsuarioCierre(actor);
        caja.setMontoFinalDeclarado(contado);
        caja.setDiferencia(contado.subtract(esperado));
        caja.setEstado(EstadoCaja.CERRADA);
        caja.setCajaAbierta(null);   // libera el UNIQUE: ya se puede abrir otra
        caja.setObservaciones(concatenar(caja.getObservaciones(), datos.observaciones()));
        cajaRepository.save(caja);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.CERRAR_CAJA, "CAJA", caja.getId(),
                "Cerró la caja: contó S/ " + contado + ", se esperaba S/ " + esperado
                        + (caja.getDiferencia().signum() == 0 ? " (cuadra)"
                            : caja.getDiferencia().signum() > 0 ? " (sobrante)" : " (faltante)"));
        return aDTO(caja);
    }

    // ---------------- los cobros (la única entrada del dinero) ----------------

    /**
     * Se cobra el precio de la copia al devolver el préstamo (fecha fija o abierto).
     * Si la copia no tiene precio, no hay cobro: la devolución se registra igual.
     */
    @Transactional
    public CajaMovimientoDTO registrarCobroPrestamo(Prestamo prestamo, Devolucion devolucion,
                                                    MetodoPago metodo, Long idActor) {
        BigDecimal precio = prestamo.getPrecioCopia();
        if (precio == null) {
            return null;   // sin precio no hay cobro y no hace falta caja
        }
        Usuario actor = usuarioService.buscarPersonal(idActor);
        boolean abierto = prestamo.getTipo() == TipoPrestamo.ABIERTO;
        String titulo = prestamo.getEjemplar().getLibro().getTitulo();
        String descripcion = "Préstamo de '" + titulo + "' (" + (abierto ? "abierto" : "fecha fija") + ")";
        return registrar(
                abierto ? TipoMovimientoCaja.COBRO_PRESTAMO_ABIERTO : TipoMovimientoCaja.COBRO_PRESTAMO_FECHA_FIJA,
                precio, BigDecimal.ZERO, metodo, descripcion,
                prestamo.getId(), null, devolucion.getId(), null, actor);
    }

    /**
     * Se cobra una multa. A la caja entra el efectivo REAL recibido
     * (pago.getMontoPagado()); el 15% de descuento en efectivo queda en
     * pago.getDescuento(): es deuda que se condona, no dinero que entró.
     */
    @Transactional
    public CajaMovimientoDTO registrarCobroMulta(PagoMulta pago, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        Multa multa = pago.getMulta();
        String titulo = multa.getDevolucion().getPrestamo().getEjemplar().getLibro().getTitulo();
        String descripcion = "Multa de '" + titulo + "' (" + multa.getDevolucion().getDiasRetraso()
                + " día(s) de retraso)";
        return registrar(TipoMovimientoCaja.COBRO_MULTA, pago.getMontoPagado(), pago.getDescuento(),
                pago.getMetodoPago(), descripcion, null, pago.getId(), multa.getDevolucion().getId(), null, actor);
    }

    /** Se cobra un libro virtual (en el mostrador o por Yape). */
    @Transactional
    public CajaMovimientoDTO registrarCobroVirtual(AccesoVirtual orden, MetodoPago metodo, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        boolean alquiler = orden.getTipo() == TipoAccesoVirtual.ALQUILER;
        String descripcion = (alquiler ? "Alquiler" : "Compra") + " de '" + orden.getLibro().getTitulo() + "'";
        return registrar(
                alquiler ? TipoMovimientoCaja.ALQUILER_VIRTUAL : TipoMovimientoCaja.VENTA_VIRTUAL,
                orden.getPrecio(), BigDecimal.ZERO, metodo, descripcion,
                null, null, null, orden.getId(), actor);
    }

    /** Falla si no hay caja abierta. Se usa antes de empezar un cobro. */
    @Transactional(readOnly = true)
    public void exigirCajaAbierta() {
        if (cajaRepository.findByCajaAbierta(MARCA_ABIERTA).isEmpty()) {
            throw new OperacionInvalidaException(
                    "No hay ninguna caja abierta. Ábrela en la sección Caja para poder cobrar");
        }
    }

    // ---------------- consultas ----------------

    /** "Qué libros faltan": préstamos sin devolver, reservas y pedidos sin cobrar. */
    @Transactional(readOnly = true)
    public PendientesCajaDTO pendientes() {
        LocalDate hoy = LocalDate.now();

        List<Prestamo> prestamos = new ArrayList<>(
                prestamoRepository.findByEstadoOrderByFechaVencimientoAsc(EstadoPrestamo.ACTIVO));
        prestamos.sort(Comparator
                .comparing(Prestamo::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Prestamo::getFechaPrestamo, Comparator.nullsLast(Comparator.naturalOrder())));

        List<PrestamoPendienteDTO> prestamosDTO = prestamos.stream().map(p -> new PrestamoPendienteDTO(
                p.getId(), p.getLector().getNombreCompleto(), p.getEjemplar().getLibro().getTitulo(),
                p.getTipo().name(), p.getFechaPrestamo(), p.getFechaVencimiento(), p.getPrecioCopia(),
                p.getTipo() == TipoPrestamo.ABIERTO ? 0L : Math.max(0L,
                        ChronoUnit.DAYS.between(p.getFechaVencimiento(), hoy)))).toList();

        List<Reserva> porRecoger = reservaRepository
                .findByEstadoInOrderByFechaReservaAscIdAsc(EnumSet.of(EstadoReserva.NOTIFICADA));
        List<Reserva> enCola = reservaRepository
                .findByEstadoInOrderByFechaReservaAscIdAsc(EnumSet.of(EstadoReserva.PENDIENTE));

        List<ReservaPendienteDTO> porRecogerDTO = porRecoger.stream().map(r -> new ReservaPendienteDTO(
                r.getId(), r.getLector().getNombreCompleto(), r.getLibro().getTitulo(), r.getFechaReserva(),
                r.getFechaLimiteRetiro(),
                r.getFechaLimiteRetiro() == null ? null : ChronoUnit.DAYS.between(hoy, r.getFechaLimiteRetiro()))).toList();

        List<ReservaPendienteDTO> enColaDTO = enCola.stream().map(r -> new ReservaPendienteDTO(
                r.getId(), r.getLector().getNombreCompleto(), r.getLibro().getTitulo(), r.getFechaReserva(),
                null, null)).toList();

        List<AccesoVirtual> pedidos = accesoRepository.findByEstadoOrderByFechaCreacionAsc(EstadoAccesoVirtual.PENDIENTE_PAGO);
        Map<Long, String> estadoComprobante = estadoComprobantePorPedido(pedidos);
        List<PedidoVirtualPendienteDTO> pedidosDTO = pedidos.stream().map(o -> new PedidoVirtualPendienteDTO(
                o.getId(), nombreDe(o), o.getLibro().getTitulo(), o.getTipo().name(),
                o.getMetodoPago() == null ? null : o.getMetodoPago().name(), o.getPrecio(),
                o.getFechaCreacion(), estadoComprobante.get(o.getId()))).toList();

        return new PendientesCajaDTO(prestamosDTO, porRecogerDTO, enColaDTO, pedidosDTO);
    }

    /** Lo que va en la pestaña "Hoy": la caja abierta, el total del día y el desglose. */
    @Transactional(readOnly = true)
    public ResumenCajaDTO resumen() {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        BigDecimal totalDia = movimientoRepository.sumarPorRango(inicioHoy, finHoy);

        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        for (Object[] fila : movimientoRepository.totalPorMetodoEnRango(inicioHoy, finHoy)) {
            porMetodo.put(String.valueOf(fila[0]), (BigDecimal) fila[1]);
        }
        Map<String, BigDecimal> porTipo = new LinkedHashMap<>();
        for (Object[] fila : movimientoRepository.totalPorTipoEnRango(inicioHoy, finHoy)) {
            porTipo.put(String.valueOf(fila[0]), (BigDecimal) fila[1]);
        }

        Caja abierta = cajaRepository.findByCajaAbierta(MARCA_ABIERTA).orElse(null);
        List<CajaMovimientoDTO> movimientos = abierta == null ? List.of()
                : movimientoRepository.findByCajaIdOrderByFechaAsc(abierta.getId())
                        .stream().map(this::movimientoADTO).toList();
        BigDecimal totalCaja = abierta == null ? CERO : movimientoRepository.sumarPorCaja(abierta.getId());

        return new ResumenCajaDTO(abierta == null ? null : aDTO(abierta), totalDia, totalCaja,
                porMetodo, porTipo, movimientos);
    }

    /** Historial de cajas (cerradas y abiertas) del rango de fechas. */
    @Transactional(readOnly = true)
    public List<CajaResponseDTO> historial(LocalDate desde, LocalDate hasta) {
        LocalDate[] r = ReporteService.rango(desde, hasta);
        return cajaRepository
                .findByFechaAperturaBetweenOrderByFechaAperturaDesc(r[0].atStartOfDay(), r[1].atTime(LocalTime.MAX))
                .stream().map(this::aDTO).toList();
    }

    /** Una caja con todos sus movimientos. */
    @Transactional(readOnly = true)
    public CajaDetalleDTO detalle(Long idCaja) {
        Caja caja = cajaRepository.findById(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        List<CajaMovimientoDTO> movimientos = movimientoRepository.findByCajaIdOrderByFechaAsc(idCaja)
                .stream().map(this::movimientoADTO).toList();
        return new CajaDetalleDTO(aDTO(caja), movimientos);
    }

    // ---------------- internos ----------------

    /** Único punto por donde entra dinero a la caja. Exige que haya una abierta. */
    private CajaMovimientoDTO registrar(TipoMovimientoCaja tipo, BigDecimal monto, BigDecimal descuento,
                                        MetodoPago metodo, String descripcion, Long idPrestamo, Long idPagoMulta,
                                        Long idDevolucion, Long idAccesoVirtual, Usuario actor) {
        Caja caja = cajaAbiertaBloqueada();
        CajaMovimiento m = new CajaMovimiento();
        m.setCaja(caja);
        m.setTipo(tipo);
        m.setMonto(dosDecimales(monto));
        m.setDescuento(descuento == null ? CERO : dosDecimales(descuento));
        m.setMetodoPago(metodo);
        m.setDescripcion(recortar(descripcion));
        m.setFecha(LocalDateTime.now());
        m.setUsuario(actor);
        m.setIdPrestamo(idPrestamo);
        m.setIdPagoMulta(idPagoMulta);
        m.setIdDevolucion(idDevolucion);
        m.setIdAccesoVirtual(idAccesoVirtual);
        return movimientoADTO(movimientoRepository.save(m));
    }

    /** La caja abierta, bloqueada para que dos cobros a la vez se serialicen. */
    private Caja cajaAbiertaBloqueada() {
        List<Caja> abiertas = cajaRepository.findByEstadoOrderByFechaAperturaDescForUpdate(EstadoCaja.ABIERTA);
        if (abiertas.isEmpty()) {
            throw new OperacionInvalidaException(
                    "No hay ninguna caja abierta. Ábrela en la sección Caja para poder cobrar");
        }
        return abiertas.get(0);
    }

    /** Estado del último comprobante de cada pedido pendiente (para saber si está en revisión). */
    private Map<Long, String> estadoComprobantePorPedido(List<AccesoVirtual> pedidos) {
        Map<Long, String> porPedido = new HashMap<>();
        if (pedidos.isEmpty()) {
            return porPedido;
        }
        List<Long> ids = pedidos.stream().map(AccesoVirtual::getId).toList();
        for (ComprobantePago c : comprobanteRepository.findByAccesoIdInOrderByFechaEnvioDesc(ids)) {
            porPedido.putIfAbsent(c.getAcceso().getId(), c.getEstado().name());
        }
        return porPedido;
    }

    private CajaResponseDTO aDTO(Caja caja) {
        BigDecimal cobrado = movimientoRepository.sumarPorCaja(caja.getId());
        BigDecimal efectivo = movimientoRepository.sumarEfectivoPorCaja(caja.getId());
        BigDecimal esperado = caja.getMontoInicial().add(efectivo);
        return new CajaResponseDTO(caja.getId(), caja.getFechaApertura(), caja.getFechaCierre(),
                caja.getUsuarioApertura() == null ? null : caja.getUsuarioApertura().getNombreCompleto(),
                caja.getUsuarioCierre() == null ? null : caja.getUsuarioCierre().getNombreCompleto(),
                caja.getMontoInicial(), caja.getMontoFinalDeclarado(), esperado, caja.getDiferencia(),
                caja.getEstado().name(), caja.getObservaciones(), cobrado);
    }

    private CajaMovimientoDTO movimientoADTO(CajaMovimiento m) {
        return new CajaMovimientoDTO(m.getId(), m.getCaja().getId(), m.getTipo().name(), m.getMonto(),
                m.getDescuento(), m.getMetodoPago().name(), m.getDescripcion(), m.getFecha(),
                m.getUsuario() == null ? null : m.getUsuario().getNombreCompleto(),
                m.getIdPrestamo(), m.getIdPagoMulta(), m.getIdDevolucion(), m.getIdAccesoVirtual());
    }

    private static String nombreDe(AccesoVirtual o) {
        return o.getUsuario().getNombreCompleto();
    }

    private static BigDecimal dosDecimales(BigDecimal valor) {
        return valor == null ? CERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        if (t.isEmpty()) return null;
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    /** Une la observación de apertura con la de cierre sin pasarse de 300 caracteres. */
    private static String concatenar(String anterior, String nuevo) {
        String a = recortar(anterior);
        String n = recortar(nuevo);
        if (n == null) return a;
        if (a == null) return n;
        return recortar(a + " | " + n);
    }
}
