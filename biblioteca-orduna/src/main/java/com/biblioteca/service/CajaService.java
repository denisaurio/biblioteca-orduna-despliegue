package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AccesoDenegadoException;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * La caja: el cajón de dinero de la biblioteca.
 *
 * Es UNA SOLA para toda la biblioteca: solo puede haber una abierta a la vez y el
 * personal se turna. Lo garantiza la base de datos (columna "caja_abierta" con
 * índice UNIQUE) y no solo el código. Cada apertura es una caja numerada (#1, #2...).
 *
 * Solo el GERENTE abre y cierra la caja. Al abrirla elige un ENCARGADO: solo el
 * encargado (o el gerente) cobra. Los demás bibliotecarios prestan y devuelven, pero
 * no reciben dinero (sus préstamos quedan POR_COBRAR). El gerente puede cambiar el
 * encargado sin cerrar la caja (historial en caja_encargados). Al final el encargado
 * cuenta el efectivo (arqueo) y el gerente cierra.
 *
 * Las cajas se relacionan por los préstamos: cada préstamo guarda la caja donde
 * empezó (y se cobró) y la caja donde se devolvió; cada pago de multa queda en la
 * caja donde se pagó (su movimiento).
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

    /** El cierre automático se decide en hora de Lima, igual que el cron. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final CajaRepository cajaRepository;
    private final CajaMovimientoRepository movimientoRepository;
    private final HorarioCajaRepository horarioCajaRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final PrestamoRepository prestamoRepository;
    private final ReservaRepository reservaRepository;
    private final AccesoVirtualRepository accesoRepository;
    private final MultaRepository multaRepository;
    private final CajaEncargadoRepository encargadoRepository;
    private final DevolucionRepository devolucionRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public CajaService(CajaRepository cajaRepository, CajaMovimientoRepository movimientoRepository,
                       HorarioCajaRepository horarioCajaRepository, ComprobantePagoRepository comprobanteRepository,
                       PrestamoRepository prestamoRepository, ReservaRepository reservaRepository,
                       AccesoVirtualRepository accesoRepository, MultaRepository multaRepository,
                       CajaEncargadoRepository encargadoRepository, DevolucionRepository devolucionRepository,
                       UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.horarioCajaRepository = horarioCajaRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.prestamoRepository = prestamoRepository;
        this.reservaRepository = reservaRepository;
        this.accesoRepository = accesoRepository;
        this.multaRepository = multaRepository;
        this.encargadoRepository = encargadoRepository;
        this.devolucionRepository = devolucionRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    // ---------------- abrir, encargado, arqueo y cierre ----------------

    /** La caja abierta ahora (null si no hay ninguna). */
    @Transactional(readOnly = true)
    public CajaResponseDTO cajaAbierta() {
        return cajaRepository.findByCajaAbierta(MARCA_ABIERTA).map(this::aDTO).orElse(null);
    }

    /**
     * Abrir el cajón (solo el gerente). Se registra el monto inicial (el dinero que ya
     * había dentro) y el ENCARGADO: el empleado que cobrará en esta caja.
     * Si ya hay una caja abierta, se dice quién la abrió y cuándo.
     */
    @Transactional
    public CajaResponseDTO abrir(AbrirCajaDTO datos, Long idActor) {
        Usuario actor = exigirGerente(idActor, "abrir la caja");
        Usuario encargado = encargadoValido(datos.idEncargado());
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
        caja.setEncargado(encargado);
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
        nuevoTramo(caja, encargado, actor, caja.getFechaApertura());
        auditoriaService.registrar(idActor, TipoAccionAuditoria.ABRIR_CAJA, "CAJA", caja.getId(),
                "Abrió la caja #" + caja.getId() + " con S/ " + caja.getMontoInicial()
                        + " de monto inicial. Encargado: " + encargado.getNombreCompleto());
        return aDTO(caja);
    }

    /**
     * El gerente cambia el encargado sin cerrar la caja (por ejemplo, cambio de turno).
     * Se cierra el tramo del encargado anterior y empieza el del nuevo. Lo que contó el
     * encargado anterior se borra: el arqueo lo hace quien entrega la caja al final.
     */
    @Transactional
    public CajaResponseDTO cambiarEncargado(Long idCaja, AsignarEncargadoDTO datos, Long idActor) {
        Usuario actor = exigirGerente(idActor, "cambiar el encargado de la caja");
        Caja caja = cajaRepository.findByIdForUpdate(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new OperacionInvalidaException("Esa caja ya está cerrada");
        }
        Usuario nuevo = encargadoValido(datos.idEncargado());
        Usuario anterior = caja.getEncargado();
        if (anterior != null && anterior.getId().equals(nuevo.getId())) {
            throw new OperacionInvalidaException(nuevo.getNombreCompleto() + " ya es el encargado de esta caja");
        }
        LocalDateTime ahora = LocalDateTime.now();
        cerrarTramo(caja, ahora);
        nuevoTramo(caja, nuevo, actor, ahora);
        caja.setEncargado(nuevo);
        caja.setMontoContadoEncargado(null);
        caja.setFechaArqueo(null);
        caja.setUsuarioArqueo(null);
        cajaRepository.save(caja);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.ASIGNAR_ENCARGADO, "CAJA", caja.getId(),
                "Cambió el encargado de la caja #" + caja.getId() + ": "
                        + (anterior == null ? "(nadie)" : anterior.getNombreCompleto())
                        + " -> " + nuevo.getNombreCompleto());
        return aDTO(caja);
    }

    /**
     * Arqueo: el encargado (o el gerente) cuenta el efectivo del cajón y lo declara.
     * La caja sigue abierta; el gerente la cierra después con ese monto. Se puede
     * volver a declarar (por ejemplo, si se contó mal).
     */
    @Transactional
    public CajaResponseDTO arquear(Long idCaja, ArqueoCajaDTO datos, Long idActor) {
        Usuario actor = usuarioService.buscarPersonal(idActor);
        Caja caja = cajaRepository.findByIdForUpdate(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new OperacionInvalidaException("Esa caja ya está cerrada");
        }
        if (!puedeCobrar(caja, actor)) {
            throw new AccesoDenegadoException("Solo el encargado de la caja ("
                    + nombreEncargado(caja) + ") o el administrador cuentan el efectivo");
        }
        BigDecimal contado = dosDecimales(datos.montoContado());
        BigDecimal esperado = caja.getMontoInicial().add(movimientoRepository.sumarEfectivoPorCaja(idCaja));
        caja.setMontoContadoEncargado(contado);
        caja.setFechaArqueo(LocalDateTime.now());
        caja.setUsuarioArqueo(actor);
        caja.setObservaciones(concatenar(caja.getObservaciones(), datos.observaciones()));
        cajaRepository.save(caja);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.ARQUEAR_CAJA, "CAJA", caja.getId(),
                "Contó S/ " + contado + " en el cajón de la caja #" + caja.getId()
                        + " (se esperaba S/ " + esperado + ")");
        return aDTO(caja);
    }

    /**
     * Cerrar el cajón (solo el gerente): el efectivo contado se compara con lo que
     * debería haber. El contado es el que escribe el gerente o, si no escribe nada, el
     * que declaró el encargado en el arqueo.
     * La diferencia es el sobrante (positivo) o el faltante (negativo).
     */
    @Transactional
    public CajaResponseDTO cerrar(Long idCaja, CerrarCajaDTO datos, Long idActor) {
        Usuario actor = exigirGerente(idActor, "cerrar la caja");
        Caja caja = cajaRepository.findByIdForUpdate(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new OperacionInvalidaException("Esa caja ya está cerrada");
        }
        BigDecimal contado;
        if (datos.montoFinalDeclarado() != null) {
            contado = dosDecimales(datos.montoFinalDeclarado());
        } else if (caja.getMontoContadoEncargado() != null) {
            contado = caja.getMontoContadoEncargado();
        } else {
            throw new OperacionInvalidaException("El encargado todavía no contó el efectivo. "
                    + "Pídele que haga el arqueo o escribe tú el monto contado");
        }
        BigDecimal efectivo = movimientoRepository.sumarEfectivoPorCaja(idCaja);
        BigDecimal esperado = caja.getMontoInicial().add(efectivo);

        caja.setFechaCierre(LocalDateTime.now());
        caja.setUsuarioCierre(actor);
        caja.setMontoFinalDeclarado(contado);
        caja.setDiferencia(contado.subtract(esperado));
        caja.setEstado(EstadoCaja.CERRADA);
        caja.setCajaAbierta(null);   // libera el UNIQUE: ya se puede abrir otra
        caja.setObservaciones(concatenar(caja.getObservaciones(), datos.observaciones()));
        cerrarTramo(caja, caja.getFechaCierre());
        cajaRepository.save(caja);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.CERRAR_CAJA, "CAJA", caja.getId(),
                "Cerró la caja #" + caja.getId() + ": contó S/ " + contado + ", se esperaba S/ " + esperado
                        + (caja.getDiferencia().signum() == 0 ? " (cuadra)"
                            : caja.getDiferencia().signum() > 0 ? " (sobrante)" : " (faltante)"));
        return aDTO(caja);
    }

    /**
     * Cierre automático por horario. Lo llama la tarea programada cada minuto.
     * Solo cierra la caja abierta HOY cuando ya pasó la hora de fin configurada
     * para hoy y la caja se abrió antes de esa hora. Devuelve true si la cerró.
     *
     * No se cierra nada si hoy no hay horario: sin configuración no hay cierre
     * automático. Tampoco se vuelve a cerrar dos veces: una vez cerrada ya no
     * está en estado ABIERTA. El arqueo automático asume lo esperado (no hay
     * nadie contando el efectivo), y eso queda dicho en las observaciones.
     */
    @Transactional
    public boolean cerrarAutomaticoPorHorario() {
        // Mismo orden de bloqueo que los cobros: primero la caja, para que un
        // cobro a la vez no se cruce con el cierre automático.
        Caja caja = cajaRepository.findByEstadoOrderByFechaAperturaDescForUpdate(EstadoCaja.ABIERTA)
                .stream().findFirst().orElse(null);
        if (caja == null) return false;

        LocalDate hoy = LocalDate.now(ZONA);
        HorarioCaja horario = horarioCajaRepository.findByDiaSemana(hoy.getDayOfWeek()).orElse(null);
        if (horario == null || horario.getHoraFin() == null) return false;

        LocalDateTime ahora = LocalDateTime.now(ZONA);
        LocalDateTime cierre = hoy.atTime(horario.getHoraFin());
        if (ahora.isBefore(cierre)) return false;

        LocalDateTime apertura = caja.getFechaApertura();
        if (apertura == null || !apertura.toLocalDate().equals(hoy) || !apertura.isBefore(cierre)) return false;

        BigDecimal esperado = caja.getMontoInicial().add(movimientoRepository.sumarEfectivoPorCaja(caja.getId()));
        BigDecimal declarado = dosDecimales(esperado);
        Usuario responsable = horario.getEncargado() != null ? horario.getEncargado()
                : (caja.getEncargado() != null ? caja.getEncargado() : caja.getUsuarioApertura());
        caja.setFechaCierre(ahora);
        caja.setUsuarioCierre(responsable);
        caja.setMontoFinalDeclarado(declarado);
        // Arqueo automático: nadie contó el efectivo, se asume lo esperado.
        caja.setMontoContadoEncargado(declarado);
        caja.setFechaArqueo(ahora);
        caja.setUsuarioArqueo(responsable);
        caja.setDiferencia(CERO);
        caja.setEstado(EstadoCaja.CERRADA);
        caja.setCajaAbierta(null);   // libera el UNIQUE: ya se puede abrir otra
        caja.setObservaciones(concatenar(caja.getObservaciones(), "Cierre automático por horario a las "
                + horario.getHoraFin() + " (arqueo automático: se asume el efectivo esperado)"));
        cerrarTramo(caja, ahora);
        cajaRepository.save(caja);
        auditoriaService.registrar(null, TipoAccionAuditoria.CERRAR_CAJA_AUTOMATICO, "CAJA", caja.getId(),
                "Cerró la caja sola por horario (" + cierre.format(FECHA_HORA) + ")");
        return true;
    }

    // ---------------- los cobros (la única entrada del dinero) ----------------

    /**
     * Se cobra el precio de la copia. Préstamos nuevos: AL PRESTAR (devolucion = null).
     * Préstamos antiguos (cobro_al_prestar = false): al devolver.
     * Si la copia no tiene precio, no hay cobro.
     */
    @Transactional
    public CajaMovimientoDTO registrarCobroPrestamo(Prestamo prestamo, Devolucion devolucion,
                                                    MetodoPago metodo, Long idActor) {
        BigDecimal precio = prestamo.getPrecioCopia();
        if (precio == null) {
            return null;   // sin precio no hay cobro
        }
        Usuario actor = usuarioService.buscarPersonal(idActor);
        boolean abierto = prestamo.getTipo() == TipoPrestamo.ABIERTO;
        String titulo = prestamo.getEjemplar().getLibro().getTitulo();
        // Fijo y personalizado usan el mismo tipo de movimiento (COBRO_PRESTAMO_FECHA_FIJA): los dos
        // tienen fecha de devolución. ABIERTO solo queda para préstamos antiguos.
        String descripcion = "Préstamo de '" + titulo + "' a " + prestamo.getLector().getNombreCompleto()
                + " (" + PrestamoService.nombreTipo(prestamo.getTipo()) + ")"
                + (devolucion == null ? "" : " · cobrado al devolver");
        return registrar(
                abierto ? TipoMovimientoCaja.COBRO_PRESTAMO_ABIERTO : TipoMovimientoCaja.COBRO_PRESTAMO_FECHA_FIJA,
                precio, BigDecimal.ZERO, metodo, descripcion,
                prestamo.getId(), null, devolucion == null ? null : devolucion.getId(), null, actor);
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
        String concepto = multa.getConcepto() == null || multa.getConcepto().isBlank()
                ? multa.getDevolucion().getDiasRetraso() + " día(s) de retraso"
                : multa.getConcepto();
        String descripcion = "Multa de '" + titulo + "' a " + multa.getUsuario().getNombreCompleto()
                + " (" + concepto + ")";
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

    /**
     * Falla si no hay caja abierta o si quien cobra no es el encargado ni el gerente.
     * Se usa antes de empezar un cobro, para no dejar nada a medias.
     */
    @Transactional
    public void exigirPuedeCobrar(Long idActor) {
        Caja caja = cajaAbiertaParaOperar();
        Usuario actor = usuarioService.buscarPersonal(idActor);
        if (!puedeCobrar(caja, actor)) {
            throw new OperacionInvalidaException(mensajeSoloEncargado(caja));
        }
    }

    /**
     * La caja abierta (para guardar en qué caja empieza o termina un préstamo), BLOQUEADA
     * hasta el final de la transacción. Sin caja abierta no se presta ni se devuelve: así
     * cada préstamo queda relacionado con sus cajas.
     *
     * ORDEN DE BLOQUEO (no lo cambies): préstamo, devolución, cobro y pago de multa toman
     * PRIMERO la caja y después el préstamo, el ejemplar o la multa. Si se toma la caja al
     * final, dos operaciones a la vez se bloquean en cruce: al guardar un préstamo con
     * id_caja_inicio, MySQL pone un bloqueo compartido sobre la fila de la caja (por la
     * clave foránea) y el cobro pide después uno exclusivo. Con 40 préstamos simultáneos
     * fallaban 32 ("Deadlock found"). Tomándola primero, las operaciones esperan en fila.
     */
    @Transactional
    public Caja cajaAbiertaParaOperar() {
        return cajaRepository.findByEstadoOrderByFechaAperturaDescForUpdate(EstadoCaja.ABIERTA).stream()
                .findFirst().orElseThrow(() -> new OperacionInvalidaException(
                "No hay ninguna caja abierta. El administrador debe abrirla (y elegir al encargado) para poder operar"));
    }

    /** true si el usuario es el encargado de la caja abierta o el gerente. */
    @Transactional(readOnly = true)
    public boolean puedeCobrarAhora(Long idActor) {
        Caja caja = cajaRepository.findByCajaAbierta(MARCA_ABIERTA).orElse(null);
        return caja != null && puedeCobrar(caja, usuarioService.buscar(idActor));
    }

    /** Solo el encargado de la caja y el gerente cobran. */
    static boolean puedeCobrar(Caja caja, Usuario actor) {
        return actor.getRol() == Rol.GERENTE
                || (caja.getEncargado() != null && caja.getEncargado().getId().equals(actor.getId()));
    }

    static String mensajeSoloEncargado(Caja caja) {
        return "Solo el encargado de la caja (" + nombreEncargado(caja) + ") o el administrador pueden cobrar";
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

        // precio_a_cobrar: solo los préstamos antiguos se cobran al devolver.
        List<PrestamoPendienteDTO> prestamosDTO = prestamos.stream().map(p -> new PrestamoPendienteDTO(
                p.getId(), p.getLector().getNombreCompleto(), p.getEjemplar().getLibro().getTitulo(),
                p.getTipo().name(), p.getFechaPrestamo(), p.getFechaVencimiento(),
                p.isCobroAlPrestar() ? null : p.getPrecioCopia(),
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

    /**
     * Lo que va en la pestaña "Hoy": la caja abierta, el total del día, el desglose,
     * si el usuario logueado puede cobrar y los préstamos que esperan su cobro.
     */
    @Transactional(readOnly = true)
    public ResumenCajaDTO resumen(Long idActor) {
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

        boolean puedoCobrar = abierta != null && puedeCobrar(abierta, usuarioService.buscar(idActor));
        List<PrestamoResponseDTO> porCobrar = prestamoRepository
                .findByEstadoOrderByFechaPrestamoAsc(EstadoPrestamo.POR_COBRAR)
                .stream().map(PrestamoService::aDTO).toList();

        return new ResumenCajaDTO(abierta == null ? null : aDTO(abierta), totalDia, totalCaja,
                porMetodo, porTipo, movimientos, puedoCobrar, porCobrar);
    }

    /** Historial de cajas (cerradas y abiertas) del rango de fechas. */
    @Transactional(readOnly = true)
    public List<CajaResponseDTO> historial(LocalDate desde, LocalDate hasta) {
        LocalDate[] r = ReporteService.rango(desde, hasta);
        return cajaRepository
                .findByFechaAperturaBetweenOrderByFechaAperturaDesc(r[0].atStartOfDay(), r[1].atTime(LocalTime.MAX))
                .stream().map(this::aDTO).toList();
    }

    /**
     * Una caja con sus movimientos y los préstamos que empezaron y terminaron en ella
     * (un préstamo puede empezar en la caja #1 y terminar en la #3).
     */
    @Transactional(readOnly = true)
    public CajaDetalleDTO detalle(Long idCaja) {
        Caja caja = cajaRepository.findById(idCaja)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la caja con id " + idCaja));
        List<CajaMovimientoDTO> movimientos = movimientoRepository.findByCajaIdOrderByFechaAsc(idCaja)
                .stream().map(this::movimientoADTO).toList();
        return new CajaDetalleDTO(aDTO(caja), movimientos,
                prestamosConCajas(prestamoRepository.findByCajaInicioIdOrderByFechaPrestamoAsc(idCaja)),
                prestamosConCajas(prestamoRepository.findByCajaFinIdOrderByFechaPrestamoAsc(idCaja)));
    }

    /**
     * Préstamos con la caja donde se pagó el precio y la fecha de devolución
     * (detalle de caja y Ficha 360). Dos consultas en bloque, no una por préstamo.
     */
    @Transactional(readOnly = true)
    public List<PrestamoResponseDTO> prestamosConCajas(List<Prestamo> prestamos) {
        if (prestamos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = prestamos.stream().map(Prestamo::getId).toList();
        Map<Long, Long> cajaCobro = new HashMap<>();
        for (CajaMovimiento m : movimientoRepository.findByIdPrestamoIn(ids)) {
            cajaCobro.put(m.getIdPrestamo(), m.getCaja().getId());
        }
        Map<Long, LocalDateTime> devuelto = new HashMap<>();
        for (Devolucion d : devolucionRepository.findByPrestamoIdIn(ids)) {
            devuelto.put(d.getPrestamo().getId(), d.getFechaDevolucion());
        }
        return prestamos.stream()
                .map(p -> PrestamoService.aDTO(p, cajaCobro.get(p.getId()), devuelto.get(p.getId())))
                .toList();
    }

    /** Caja donde se pagó cada pago de multa: id del pago -> id de la caja. */
    @Transactional(readOnly = true)
    public Map<Long, Long> cajaPorPagoMulta(Collection<Long> idsPago) {
        Map<Long, Long> r = new HashMap<>();
        if (idsPago.isEmpty()) {
            return r;
        }
        for (CajaMovimiento m : movimientoRepository.findByIdPagoMultaIn(idsPago)) {
            r.put(m.getIdPagoMulta(), m.getCaja().getId());
        }
        return r;
    }

    /** Todo lo que pagó un cliente (Ficha 360), el más reciente primero. */
    @Transactional(readOnly = true)
    public List<CajaMovimientoDTO> movimientosDeCliente(Long idCliente) {
        return movimientoRepository.movimientosDeCliente(idCliente).stream().map(this::movimientoADTO).toList();
    }

    // ---------------- internos ----------------

    /**
     * Único punto por donde entra dinero a la caja. Exige que haya una abierta y que
     * cobre su encargado o el gerente.
     */
    private CajaMovimientoDTO registrar(TipoMovimientoCaja tipo, BigDecimal monto, BigDecimal descuento,
                                        MetodoPago metodo, String descripcion, Long idPrestamo, Long idPagoMulta,
                                        Long idDevolucion, Long idAccesoVirtual, Usuario actor) {
        Caja caja = cajaAbiertaBloqueada();
        if (!puedeCobrar(caja, actor)) {
            throw new OperacionInvalidaException(mensajeSoloEncargado(caja));
        }
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
                    "No hay ninguna caja abierta. El administrador debe abrirla para poder cobrar");
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
                caja.getEstado().name(), caja.getObservaciones(), cobrado,
                caja.getEncargado() == null ? null : caja.getEncargado().getId(),
                caja.getEncargado() == null ? null : caja.getEncargado().getNombreCompleto(),
                caja.getMontoContadoEncargado(), caja.getFechaArqueo(),
                caja.getUsuarioArqueo() == null ? null : caja.getUsuarioArqueo().getNombreCompleto(),
                tramos(caja));
    }

    /** Historial de encargados. Las cajas antiguas no tienen filas: su encargado es quien la abrió. */
    private List<EncargadoCajaDTO> tramos(Caja caja) {
        List<CajaEncargado> filas = encargadoRepository.findByCajaIdOrderByDesdeAsc(caja.getId());
        if (filas.isEmpty()) {
            if (caja.getEncargado() == null) {
                return List.of();
            }
            return List.of(new EncargadoCajaDTO(caja.getEncargado().getId(), caja.getEncargado().getNombreCompleto(),
                    caja.getFechaApertura(), caja.getFechaCierre(),
                    caja.getUsuarioApertura() == null ? null : caja.getUsuarioApertura().getNombreCompleto()));
        }
        return filas.stream().map(t -> new EncargadoCajaDTO(t.getUsuario().getId(), t.getUsuario().getNombreCompleto(),
                t.getDesde(), t.getHasta(), t.getAsignadoPor().getNombreCompleto())).toList();
    }

    private void nuevoTramo(Caja caja, Usuario encargado, Usuario asignadoPor, LocalDateTime desde) {
        CajaEncargado t = new CajaEncargado();
        t.setCaja(caja);
        t.setUsuario(encargado);
        t.setAsignadoPor(asignadoPor);
        t.setDesde(desde);
        encargadoRepository.save(t);
    }

    /**
     * Cierra el tramo del encargado actual. Si la caja es anterior al historial (no tiene
     * filas), primero se crea el tramo de quien la abrió para no perder ese dato.
     */
    private void cerrarTramo(Caja caja, LocalDateTime hasta) {
        CajaEncargado actual = encargadoRepository.findFirstByCajaIdAndHastaIsNullOrderByDesdeDesc(caja.getId())
                .orElse(null);
        if (actual == null && caja.getEncargado() != null
                && encargadoRepository.findByCajaIdOrderByDesdeAsc(caja.getId()).isEmpty()) {
            actual = new CajaEncargado();
            actual.setCaja(caja);
            actual.setUsuario(caja.getEncargado());
            actual.setAsignadoPor(caja.getUsuarioApertura());
            actual.setDesde(caja.getFechaApertura());
        }
        if (actual != null) {
            actual.setHasta(hasta);
            encargadoRepository.save(actual);
        }
    }

    private Usuario exigirGerente(Long idActor, String accion) {
        Usuario u = usuarioService.buscar(idActor);
        if (u.getRol() != Rol.GERENTE) {
            throw new AccesoDenegadoException("Solo el administrador puede " + accion);
        }
        return u;
    }

    /** El encargado tiene que ser personal (bibliotecario o gerente) con la cuenta activa. */
    private Usuario encargadoValido(Long idEncargado) {
        Usuario u = usuarioService.buscarPersonal(idEncargado);
        if (!u.isActivo()) {
            throw new OperacionInvalidaException("La cuenta de " + u.getNombreCompleto() + " está desactivada");
        }
        return u;
    }

    private static String nombreEncargado(Caja caja) {
        return caja.getEncargado() == null ? "sin asignar" : caja.getEncargado().getNombreCompleto();
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
