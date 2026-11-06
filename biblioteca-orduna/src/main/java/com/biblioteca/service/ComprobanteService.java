package com.biblioteca.service;

import com.biblioteca.dto.ComprobantePagoResponseDTO;
import com.biblioteca.dto.RevisionComprobanteDTO;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AccesoDenegadoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.AccesoVirtualRepository;
import com.biblioteca.repository.ComprobantePagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprobantes de pago con Yape.
 *
 * Yape no tiene API publica con QR dinamico, asi que el pago se hace a mano:
 * el lector ve el QR de Yape Negocio, paga en su app, saca una captura y la sube
 * aqui. El personal la ve (con una URL firmada) y la ACEPTA o la DENIEGA.
 *
 * Al aceptar, el pedido de libro virtual se activa y el lector recibe el aviso de
 * "felicidades, compra exitosa". Al denegar, el pedido SIGUE pendiente: el lector
 * puede subir otra captura.
 *
 * La imagen vive en el bucket privado de R2 (como el PDF): en la base solo queda
 * la clave, y se sirve con una URL firmada que caduca.
 */
@Service
public class ComprobanteService {

    /** El enlace para ver la captura caduca en 15 minutos, como el del PDF. */
    public static final Duration VALIDEZ_ENLACE = Duration.ofMinutes(15);

    private final ComprobantePagoRepository comprobanteRepository;
    private final AccesoVirtualRepository accesoRepository;
    private final UsuarioService usuarioService;
    private final NotificacionService notificacionService;
    private final AlmacenamientoService almacenamientoService;
    private final AccesoVirtualService accesoVirtualService;
    private final AuditoriaService auditoriaService;

    public ComprobanteService(ComprobantePagoRepository comprobanteRepository,
                              AccesoVirtualRepository accesoRepository,
                              UsuarioService usuarioService,
                              NotificacionService notificacionService,
                              AlmacenamientoService almacenamientoService,
                              AccesoVirtualService accesoVirtualService,
                              AuditoriaService auditoriaService) {
        this.comprobanteRepository = comprobanteRepository;
        this.accesoRepository = accesoRepository;
        this.usuarioService = usuarioService;
        this.notificacionService = notificacionService;
        this.almacenamientoService = almacenamientoService;
        this.accesoVirtualService = accesoVirtualService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * El lector sube la captura de su pago con Yape.
     *
     * - Solo el dueno del pedido puede subirlo (AccesoDenegadoException si no).
     * - El pedido tiene que estar PENDIENTE_PAGO y pagado con YAPE.
     * - El pedido NO cambia de estado: "en revision" sale del comprobante.
     */
    @Transactional
    public ComprobantePagoResponseDTO subir(Long idAcceso, MultipartFile archivo, String referenciaYape, Long idActor) {
        Usuario actor = usuarioService.buscar(idActor);
        AccesoVirtual orden = accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + idAcceso));
        if (!orden.getUsuario().getId().equals(actor.getId())) {
            throw new AccesoDenegadoException("Este pedido no es tuyo");
        }
        if (orden.pagado()) {
            throw new OperacionInvalidaException("Este pedido ya esta pagado");
        }
        if (orden.getEstado() == EstadoAccesoVirtual.CANCELADO) {
            throw new OperacionInvalidaException("Este pedido esta cancelado");
        }
        if (orden.getMetodoPago() != MetodoPago.YAPE) {
            throw new OperacionInvalidaException("Este pedido se paga en efectivo en la tienda: no lleva comprobante");
        }

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setAcceso(orden);
        comprobante.setLector(actor);
        comprobante.setMonto(orden.getPrecio());
        comprobante.setReferenciaYape(referenciaYape == null || referenciaYape.isBlank()
                ? null : referenciaYape.trim());
        comprobante.setFechaEnvio(LocalDateTime.now());
        comprobante.setEstado(EstadoComprobante.PENDIENTE);
        comprobante = comprobanteRepository.save(comprobante);

        // La clave del objeto lleva el id del comprobante, asi que se sube despues de guardarlo.
        String clave = almacenamientoService.subirComprobante(archivo, comprobante.getId());
        comprobante.setClaveImagen(clave);
        comprobanteRepository.save(comprobante);

        notificacionService.crearParaPersonal(TipoNotificacion.COMPROBANTE_YAPE,
                "Hay un comprobante de Yape por revisar: '" + orden.getLibro().getTitulo() + "' por S/ "
                        + orden.getPrecio() + " (lector " + actor.getNombreCompleto() + ").", comprobante.getId());
        return aDTO(comprobante);
    }

    /** El personal acepta el comprobante: el pedido se activa y el lector ya tiene el PDF. */
    @Transactional
    public ComprobantePagoResponseDTO aprobar(Long idComprobante, Long idActor) {
        Usuario revisor = usuarioService.buscarPersonal(idActor);
        ComprobantePago comprobante = comprobanteRepository.findByIdForUpdate(idComprobante)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprobante con id " + idComprobante));
        if (comprobante.getEstado() != EstadoComprobante.PENDIENTE) {
            throw new OperacionInvalidaException("Este comprobante ya fue revisado");
        }
        AccesoVirtual orden = accesoRepository.findByIdForUpdate(comprobante.getAcceso().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido del comprobante ya no existe"));

        comprobante.setEstado(EstadoComprobante.APROBADO);
        comprobante.setRevisadoPor(revisor);
        comprobante.setFechaRevision(LocalDateTime.now());
        comprobante.setMotivo(null);
        comprobanteRepository.save(comprobante);

        // El pedido se activa (y avisa al lector). Si ya estaba pagado, no vuelve a avisar.
        accesoVirtualService.activar(orden, "YAPE-" + comprobante.getId(), MetodoPago.YAPE, idActor);

        auditoriaService.registrar(idActor, TipoAccionAuditoria.ACEPTAR_COMPROBANTE, "COMPROBANTE",
                comprobante.getId(), "Aceptó el comprobante de Yape de '" + orden.getLibro().getTitulo()
                        + "' (" + comprobante.getMonto() + " S/) de " + comprobante.getLector().getNombreCompleto());
        return aDTO(comprobante);
    }

    /**
     * El personal deniega el comprobante. El pedido SIGUE pendiente de pago: el lector
     * puede subir otra captura. El motivo se lo ve el lector en su aviso.
     */
    @Transactional
    public ComprobantePagoResponseDTO denegar(Long idComprobante, RevisionComprobanteDTO datos, Long idActor) {
        Usuario revisor = usuarioService.buscarPersonal(idActor);
        ComprobantePago comprobante = comprobanteRepository.findByIdForUpdate(idComprobante)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprobante con id " + idComprobante));
        if (comprobante.getEstado() != EstadoComprobante.PENDIENTE) {
            throw new OperacionInvalidaException("Este comprobante ya fue revisado");
        }
        String motivo = datos.motivo().trim();

        comprobante.setEstado(EstadoComprobante.DENEGADO);
        comprobante.setRevisadoPor(revisor);
        comprobante.setFechaRevision(LocalDateTime.now());
        comprobante.setMotivo(motivo);
        comprobanteRepository.save(comprobante);

        AccesoVirtual orden = comprobante.getAcceso();
        notificacionService.crear(comprobante.getLector(), TipoNotificacion.PAGO_RECHAZADO,
                "No aceptamos tu comprobante de Yape de '" + orden.getLibro().getTitulo() + "': " + motivo
                        + ". Puedes subir otra captura o pagar en la tienda.", orden.getId());

        auditoriaService.registrar(idActor, TipoAccionAuditoria.DENEGAR_COMPROBANTE, "COMPROBANTE",
                comprobante.getId(), "Denegó el comprobante de Yape de '" + orden.getLibro().getTitulo()
                        + "' de " + comprobante.getLector().getNombreCompleto() + ": " + motivo);
        return aDTO(comprobante);
    }

    /** Comprobantes que aun no ha revisado el personal (los mas antiguos primero). */
    @Transactional(readOnly = true)
    public List<ComprobantePagoResponseDTO> listarPendientes() {
        return comprobanteRepository.findByEstadoOrderByFechaEnvioAsc(EstadoComprobante.PENDIENTE)
                .stream().map(ComprobanteService::aDTO).toList();
    }

    /** Historial de comprobantes (pendientes, aceptados y denegados). */
    @Transactional(readOnly = true)
    public List<ComprobantePagoResponseDTO> listar(EstadoComprobante estado) {
        List<ComprobantePago> lista = estado == null
                ? comprobanteRepository.findAll()
                : comprobanteRepository.findByEstadoOrderByFechaEnvioAsc(estado);
        return lista.stream().map(ComprobanteService::aDTO).toList();
    }

    /** Los comprobantes de un pedido (solo los del lector que lo pidio). */
    @Transactional(readOnly = true)
    public List<ComprobantePagoResponseDTO> misComprobantes(Long idAcceso, Long idActor) {
        Usuario actor = usuarioService.buscar(idActor);
        AccesoVirtual orden = accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + idAcceso));
        if (!orden.getUsuario().getId().equals(actor.getId())) {
            throw new AccesoDenegadoException("Este pedido no es tuyo");
        }
        return comprobanteRepository.findByAccesoIdOrderByFechaEnvioDesc(idAcceso)
                .stream().map(ComprobanteService::aDTO).toList();
    }

    /** URL firmada de la captura (solo el personal). */
    @Transactional(readOnly = true)
    public String urlImagen(Long idComprobante, Long idActor) {
        usuarioService.buscarPersonal(idActor);
        ComprobantePago comprobante = comprobanteRepository.findById(idComprobante)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprobante con id " + idComprobante));
        return almacenamientoService.urlFirmadaComprobante(comprobante.getClaveImagen(), VALIDEZ_ENLACE);
    }

    // ---------------- interno ----------------

    static ComprobantePagoResponseDTO aDTO(ComprobantePago c) {
        AccesoVirtual orden = c.getAcceso();
        Usuario lector = c.getLector();
        Usuario revisor = c.getRevisadoPor();
        return new ComprobantePagoResponseDTO(c.getId(), orden.getId(), orden.getLibro().getTitulo(),
                lector.getId(), lector.getNombreCompleto(), lector.getEmail(),
                orden.getTipo().name(), c.getMonto(), c.getReferenciaYape(), c.getFechaEnvio(),
                c.getEstado().name(),
                revisor == null ? null : revisor.getId(),
                revisor == null ? null : revisor.getNombreCompleto(),
                c.getFechaRevision(), c.getMotivo());
    }
}
