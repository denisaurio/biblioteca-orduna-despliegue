package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AccesoDenegadoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.AccesoVirtualRepository;
import com.biblioteca.repository.ComprobantePagoRepository;
import com.biblioteca.repository.LibroRepository;
import com.biblioteca.repository.MultaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Libros virtuales: pedidos, pagos y el panel del mostrador.
 *
 * El lector pide un libro virtual (alquiler por dias o compra) y paga de dos formas:
 *   - YAPE: ve el QR de Yape Negocio, paga en su app y sube la captura. El personal
 *     la revisa y la acepta (o la deniega). No hay API de Yape con QR dinamico, asi que
 *     el monto lo teclea el cliente y el personal lo coteja.
 *   - EFECTIVO en la tienda: se le da un numero de cola y un codigo de 24 horas que
 *     muestra en el mostrador; el personal busca la orden con ese codigo y la cobra.
 *
 * El PDF vive en un bucket privado de Cloudflare R2: se entrega con una URL firmada
 * que caduca, y solo a quien ya lo pagado.
 */
@Service
public class AccesoVirtualService {

    /** El codigo de retiro vale 24 horas. */
    public static final Duration VALIDEZ_CODIGO = Duration.ofHours(24);

    /** Validez del enlace de lectura del PDF. */
    public static final Duration VALIDEZ_ENLACE_PDF = Duration.ofMinutes(15);

    private static final EnumSet<EstadoMulta> CON_DEUDA = EnumSet.of(EstadoMulta.PENDIENTE, EstadoMulta.PARCIAL);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final AccesoVirtualRepository accesoRepository;
    private final MultaRepository multaRepository;
    private final LibroRepository libroRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final UsuarioService usuarioService;
    private final SancionService sancionService;
    private final NotificacionService notificacionService;
    private final AlmacenamientoService almacenamientoService;
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    public AccesoVirtualService(AccesoVirtualRepository accesoRepository, MultaRepository multaRepository,
                                LibroRepository libroRepository, ComprobantePagoRepository comprobanteRepository,
                                UsuarioService usuarioService,
                                SancionService sancionService, NotificacionService notificacionService,
                                AlmacenamientoService almacenamientoService, CajaService cajaService,
                                AuditoriaService auditoriaService) {
        this.accesoRepository = accesoRepository;
        this.multaRepository = multaRepository;
        this.libroRepository = libroRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.usuarioService = usuarioService;
        this.sancionService = sancionService;
        this.notificacionService = notificacionService;
        this.almacenamientoService = almacenamientoService;
        this.cajaService = cajaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * El lector pide un libro virtual.
     * - OperacionInvalidaException si el libro no es virtual, no tiene PDF, ya lo tiene
     *   o tiene multas sin pagar.
     * - UsuarioSancionadoException si esta sancionado.
     */
    @Transactional
    public PedidoVirtualDTO pedir(RegistroAccesoVirtualDTO datos, Long idLector) {
        Usuario lector = usuarioService.buscarLector(idLector);
        sancionService.verificarSinSancion(lector.getId());
        if (multaRepository.existsByUsuarioIdAndEstadoIn(lector.getId(), CON_DEUDA)) {
            throw new OperacionInvalidaException("El lector tiene multas pendientes de pago");
        }

        Libro libro = libroRepository.findById(datos.idLibro())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el libro con id " + datos.idLibro()));
        if (libro.getTipoLibro() == TipoLibro.FISICO) {
            throw new OperacionInvalidaException("'" + libro.getTitulo()
                    + "' es un libro fisico: se pide en el mostrador, no como libro virtual");
        }
        if (libro.getClavePdf() == null || libro.getClavePdf().isBlank()) {
            throw new OperacionInvalidaException("'" + libro.getTitulo() + "' todavia no tiene el PDF cargado");
        }
        if (datos.metodoPago() == null) {
            throw new OperacionInvalidaException("Elige como vas a pagar");
        }
        if (datos.metodoPago() != MetodoPago.YAPE && datos.metodoPago() != MetodoPago.EFECTIVO) {
            throw new OperacionInvalidaException("Un libro virtual se paga con Yape o en efectivo en la tienda");
        }
        if (yaTieneAcceso(lector.getId(), libro.getId())) {
            throw new OperacionInvalidaException("Ya tienes acceso a '" + libro.getTitulo() + "'");
        }

        AccesoVirtual orden = new AccesoVirtual();
        orden.setUsuario(lector);
        orden.setLibro(libro);
        orden.setTipo(datos.tipo());
        orden.setEstado(EstadoAccesoVirtual.PENDIENTE_PAGO);
        orden.setMetodoPago(datos.metodoPago());
        orden.setPrecio(calcularPrecio(libro, datos.tipo(), diasDeAlquiler(datos.tipo(), datos.diasAlquiler())));
        orden.setDiasAlquiler(datos.tipo() == TipoAccesoVirtual.ALQUILER ? datos.diasAlquiler() : null);
        orden.setFechaCreacion(LocalDateTime.now());

        // Pago en la tienda: numero de cola y codigo de 24 horas para el mostrador.
        // Con Yape no hace falta: el comprobante lo sube el propio lector.
        if (datos.metodoPago() == MetodoPago.EFECTIVO) {
            orden.setNumeroCola(codigoAleatorio(6));
            orden.setCodigoRetiro(codigoRetiroLibre());
            orden.setFechaCodigoExpira(LocalDateTime.now().plus(VALIDEZ_CODIGO));
        }
        return new PedidoVirtualDTO(aDTO(accesoRepository.save(orden)));
    }

    /** Libros virtuales del lector (su perfil). Los que ya vencieron salen "en plomo". */
    @Transactional(readOnly = true)
    public List<AccesoVirtualResponseDTO> misAccesos(Long idLector) {
        return aDTOs(accesoRepository.findByUsuarioIdOrderByFechaCreacionDesc(idLector));
    }

    /** Panel del mostrador: los pedidos que aun no se han cobrado. */
    @Transactional(readOnly = true)
    public List<AccesoVirtualResponseDTO> listarPedidos(EstadoAccesoVirtual estado) {
        List<AccesoVirtual> lista = estado == null
                ? accesoRepository.findAll()
                : accesoRepository.findByEstadoOrderByFechaCreacionAsc(estado);
        return aDTOs(lista);
    }

    /** El personal busca la orden por el codigo que muestra el cliente (24 horas). */
    @Transactional(readOnly = true)
    public AccesoVirtualResponseDTO buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new OperacionInvalidaException("Escribe el codigo del cliente");
        }
        AccesoVirtual orden = accesoRepository.findByCodigoRetiro(codigo.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay ninguna orden con ese codigo"));
        if (orden.codigoExpirado(LocalDateTime.now())) {
            throw new OperacionInvalidaException("Ese codigo ya vencio (valia 24 horas)");
        }
        return aDTO(orden);
    }

    /** Cancelar la orden: el lector cancela la suya, el personal puede cancelar cualquiera. */
    @Transactional
    public AccesoVirtualResponseDTO cancelar(Long idAcceso, Long idActor) {
        Usuario actor = usuarioService.buscar(idActor);
        AccesoVirtual orden = buscar(idAcceso);
        if (!UsuarioService.esPersonal(actor) && !orden.getUsuario().getId().equals(actor.getId())) {
            throw new AccesoDenegadoException("Solo puedes cancelar tus propios pedidos");
        }
        if (orden.pagado()) {
            throw new OperacionInvalidaException("Este pedido ya esta pagado: no se puede cancelar");
        }
        orden.setEstado(EstadoAccesoVirtual.CANCELADO);
        return aDTO(accesoRepository.save(orden));
    }

    /**
     * Cambiar la orden (solo el personal). El pedido todavia no esta pagado, asi que el
     * precio se recalcula con el precio vigente del libro.
     */
    @Transactional
    public AccesoVirtualResponseDTO cambiar(Long idAcceso, CambioAccesoVirtualDTO datos, Long idActor) {
        usuarioService.buscarPersonal(idActor);
        AccesoVirtual orden = buscar(idAcceso);
        if (orden.pagado()) {
            throw new OperacionInvalidaException("Este pedido ya esta pagado: no se puede cambiar");
        }
        if (orden.getEstado() == EstadoAccesoVirtual.CANCELADO) {
            throw new OperacionInvalidaException("Este pedido esta cancelado");
        }
        TipoAccesoVirtual tipo = datos.tipo() != null ? datos.tipo() : orden.getTipo();
        Integer dias = tipo == TipoAccesoVirtual.ALQUILER
                ? (datos.diasAlquiler() != null ? datos.diasAlquiler() : orden.getDiasAlquiler())
                : null;
        int diasUsados = diasDeAlquiler(tipo, dias);
        orden.setTipo(tipo);
        orden.setDiasAlquiler(tipo == TipoAccesoVirtual.ALQUILER ? dias : null);
        orden.setPrecio(calcularPrecio(orden.getLibro(), tipo, diasUsados));
        return aDTO(accesoRepository.save(orden));
    }

    /**
     * El personal cobra la orden en el mostrador: el lector ya tiene el libro.
     * La orden se bloquea para que dos personas no la cobren a la vez.
     */
    @Transactional
    public AccesoVirtualResponseDTO cobrar(Long idAcceso, Long idActor) {
        usuarioService.buscarPersonal(idActor);
        AccesoVirtual orden = accesoRepository.findByIdForUpdate(idAcceso)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + idAcceso));
        if (orden.pagado()) {
            throw new OperacionInvalidaException("Este pedido ya esta pagado");
        }
        if (orden.getEstado() == EstadoAccesoVirtual.CANCELADO) {
            throw new OperacionInvalidaException("Este pedido esta cancelado");
        }
        AccesoVirtualResponseDTO dto = activar(orden, "MOSTRADOR-" + orden.getId(), MetodoPago.EFECTIVO, idActor);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.COBRAR_PEDIDO_VIRTUAL, "ACCESO_VIRTUAL",
                orden.getId(), "Cobró en el mostrador '" + orden.getLibro().getTitulo() + "' ("
                        + orden.getTipo().name().toLowerCase() + ", S/ " + orden.getPrecio() + ") a "
                        + orden.getUsuario().getNombreCompleto());
        return dto;
    }

    /** Quien compro o alquilo un libro (lo ven el bibliotecario y el gerente). */
    @Transactional(readOnly = true)
    public List<AccesoVirtualResponseDTO> accesosDeLibro(Long idLibro) {
        return aDTOs(accesoRepository.findByLibroIdOrderByFechaCreacionDesc(idLibro));
    }

    /**
     * Enlace de lectura (o descarga) del PDF. Solo el dueño del pedido pagado, o el
     * personal. El enlace caduca en 15 minutos.
     *
     * descargar = true pide el PDF como archivo para guardar: solo se permite si el
     * pedido es una COMPRA (o si lo pide el personal). Un ALQUILER se abre para leer.
     */
    @Transactional(readOnly = true)
    public String urlPdf(Long idAcceso, Long idUsuario, boolean descargar) {
        Usuario actor = usuarioService.buscar(idUsuario);
        AccesoVirtual orden = buscar(idAcceso);
        boolean personal = UsuarioService.esPersonal(actor);
        if (!orden.getUsuario().getId().equals(actor.getId()) && !personal) {
            throw new AccesoDenegadoException("Este libro no es tuyo");
        }
        if (descargar && orden.getTipo() != TipoAccesoVirtual.COMPRA && !personal) {
            throw new AccesoDenegadoException("El alquiler es solo para leer en la aplicacion: compra el libro para descargarlo");
        }
        if (!orden.pagado()) {
            throw new OperacionInvalidaException("Este libro virtual todavia no esta pagado");
        }
        if (orden.vencido(LocalDate.now())) {
            throw new OperacionInvalidaException("Tu alquiler de este libro ya termino: reservalo de nuevo");
        }
        return almacenamientoService.urlFirmadaPdf(orden.getLibro().getClavePdf(), VALIDEZ_ENLACE_PDF,
                orden.getLibro().getTitulo(), descargar);
    }

    // ---------------- internos ----------------

    /**
     * Activa el acceso (el lector ya pago) y le avisa.
     *
     * Lo usan el mostrador (pago en efectivo) y la revision de un comprobante de Yape.
     * Es IDEMPOTENTE: si el pedido ya estaba pagado, no vuelve a notificar ni a cobrar.
     * El dinero entra a la caja: hace falta una caja abierta.
     */
    public AccesoVirtualResponseDTO activar(AccesoVirtual orden, String referenciaPago,
                                            MetodoPago metodoCobro, Long idActor) {
        if (orden.pagado()) {
            return aDTO(orden);
        }
        cajaService.exigirCajaAbierta();
        LocalDate hoy = LocalDate.now();
        orden.setEstado(EstadoAccesoVirtual.PAGADO);
        orden.setReferenciaPago(referenciaPago);
        orden.setMetodoPago(metodoCobro);
        orden.setFechaInicio(hoy);
        if (orden.getTipo() == TipoAccesoVirtual.ALQUILER) {
            int dias = orden.getDiasAlquiler() == null ? 1 : orden.getDiasAlquiler();
            orden.setFechaFin(hoy.plusDays(dias));
        } else {
            orden.setFechaFin(null);
        }
        // El codigo de retiro ya se uso: no sirve para cobrar dos veces.
        orden.setCodigoRetiro(null);
        orden.setFechaCodigoExpira(null);
        accesoRepository.save(orden);

        // El cobro entra a la caja (una sola vez).
        cajaService.registrarCobroVirtual(orden, metodoCobro, idActor);

        String titulo = orden.getLibro().getTitulo();
        if (orden.getTipo() == TipoAccesoVirtual.ALQUILER) {
            long dias = orden.diasRestantes(hoy);
            notificacionService.crear(orden.getUsuario(), TipoNotificacion.ACCESO_VIRTUAL,
                    "Felicidades, tu alquiler de '" + titulo + "' esta listo. Disfruta tus " + dias
                            + " dias restantes.", orden.getId());
        } else {
            notificacionService.crear(orden.getUsuario(), TipoNotificacion.ACCESO_VIRTUAL,
                    "Felicidades, compra exitosa. Disfruta de tu libro pagado: '" + titulo + "'.", orden.getId());
        }
        return aDTO(orden);
    }

    /** El lector ya tiene este libro (pagado y vigente, o pendiente de pago). */
    private boolean yaTieneAcceso(Long idLector, Long idLibro) {
        LocalDate hoy = LocalDate.now();
        return accesoRepository.findByUsuarioIdAndLibroIdOrderByFechaCreacionDesc(idLector, idLibro).stream()
                .anyMatch(o -> o.getEstado() == EstadoAccesoVirtual.PENDIENTE_PAGO
                        || (o.pagado() && !o.vencido(hoy)));
    }

    private BigDecimal calcularPrecio(Libro libro, TipoAccesoVirtual tipo, int dias) {
        if (tipo == TipoAccesoVirtual.COMPRA) {
            if (libro.getPrecioCompra() == null) {
                throw new OperacionInvalidaException("Este libro no tiene precio de compra");
            }
            return libro.getPrecioCompra();
        }
        if (libro.getPrecioAlquilerDiario() == null) {
            throw new OperacionInvalidaException("Este libro no tiene precio de alquiler");
        }
        return libro.getPrecioAlquilerDiario().multiply(BigDecimal.valueOf(dias)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private int diasDeAlquiler(TipoAccesoVirtual tipo, Integer dias) {
        if (tipo != TipoAccesoVirtual.ALQUILER) return 0;
        if (dias == null || dias < 1) {
            throw new OperacionInvalidaException("Elige cuantos dias vas a alquilar el libro");
        }
        if (dias > 365) {
            throw new OperacionInvalidaException("El alquiler es de hasta 365 dias");
        }
        return dias;
    }

    private AccesoVirtual buscar(Long idAcceso) {
        return accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + idAcceso));
    }

    /** Codigo de retiro libre: 8 caracteres sin ambiguedad (no hay 0 ni O ni 1 ni I). */
    private String codigoRetiroLibre() {
        for (int intento = 0; intento < 20; intento++) {
            String codigo = codigoAleatorio(8);
            if (!accesoRepository.existsByCodigoRetiro(codigo)) {
                return codigo;
            }
        }
        throw new OperacionInvalidaException("No se pudo generar un codigo de retiro. Intente de nuevo");
    }

    private static String codigoAleatorio(int longitud) {
        StringBuilder sb = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** DTO de un pedido, con el estado del ultimo comprobante de Yape (dato derivado). */
    AccesoVirtualResponseDTO aDTO(AccesoVirtual o) {
        ComprobantePago comprobante = comprobanteRepository
                .findFirstByAccesoIdOrderByFechaEnvioDesc(o.getId())
                .orElse(null);
        return aDTO(o, comprobante);
    }

    /** DTOs de varios pedidos: los comprobantes se traen de una sola vez (sin consulta por fila). */
    List<AccesoVirtualResponseDTO> aDTOs(List<AccesoVirtual> lista) {
        Map<Long, ComprobantePago> porAcceso = new HashMap<>();
        if (!lista.isEmpty()) {
            List<Long> ids = lista.stream().map(AccesoVirtual::getId).toList();
            // La consulta viene de mas reciente a mas antigua: el primero de cada pedido es el ultimo.
            for (ComprobantePago c : comprobanteRepository.findByAccesoIdInOrderByFechaEnvioDesc(ids)) {
                porAcceso.putIfAbsent(c.getAcceso().getId(), c);
            }
        }
        return lista.stream().map(o -> aDTO(o, porAcceso.get(o.getId()))).toList();
    }

    private AccesoVirtualResponseDTO aDTO(AccesoVirtual o, ComprobantePago comprobante) {
        LocalDate hoy = LocalDate.now();
        boolean vencido = o.vencido(hoy);
        boolean pagado = o.pagado();
        boolean enRevision = comprobante != null && comprobante.getEstado() == EstadoComprobante.PENDIENTE;
        return new AccesoVirtualResponseDTO(o.getId(), o.getLibro().getId(), o.getLibro().getTitulo(),
                almacenamientoService.urlVisible(o.getLibro().getUrlImagen()), nombreDe(o), o.getUsuario().getEmail(),
                o.getTipo().name(), o.getEstado().name(), o.getPrecio(),
                o.getDiasAlquiler(), o.diasRestantes(hoy), o.getFechaInicio(), o.getFechaFin(),
                o.getMetodoPago() == null ? null : o.getMetodoPago().name(),
                o.getNumeroCola(), o.getCodigoRetiro(), o.getFechaCodigoExpira(), o.getReferenciaPago(),
                o.getFechaCreacion(), vencido, pagado && !vencido, pagado && !vencido
                && o.getTipo() == TipoAccesoVirtual.COMPRA,
                comprobante == null ? null : comprobante.getEstado().name(),
                enRevision);
    }

    /** Nombre completo del lector que tiene el acceso. */
    private String nombreDe(AccesoVirtual o) {
        Usuario u = o.getUsuario();
        String nombre = u.getNombre() == null ? "" : u.getNombre();
        String apellido = u.getApellido() == null ? "" : u.getApellido();
        return (nombre + " " + apellido).trim();
    }
}
