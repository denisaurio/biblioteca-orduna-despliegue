package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.DevolucionRepository;
import com.biblioteca.repository.MultaRepository;
import com.biblioteca.repository.NotaClienteRepository;
import com.biblioteca.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * CRM del administrador: Ficha 360 del cliente (solo el gerente).
 *
 * Junta en una pantalla todo lo de un lector: sus datos, un resumen (cuánto pagó,
 * cuánto debe, cuántos préstamos devolvió a tiempo), sus préstamos con la caja donde
 * empezaron y terminaron, sus multas con la caja donde las pagó, todos sus pagos,
 * sus sanciones y las notas internas del personal. El cliente nunca ve las notas.
 *
 * No guarda nada nuevo salvo las notas: todo lo demás se lee de las tablas que ya
 * existen (el movimiento de caja sigue siendo la única fuente del dinero).
 */
@Service
public class CrmService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final UsuarioService usuarioService;
    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;
    private final NotaClienteRepository notaRepository;
    private final DevolucionService devolucionService;
    private final SancionService sancionService;
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    public CrmService(UsuarioService usuarioService, PrestamoRepository prestamoRepository,
                      DevolucionRepository devolucionRepository, MultaRepository multaRepository,
                      NotaClienteRepository notaRepository, DevolucionService devolucionService,
                      SancionService sancionService, CajaService cajaService,
                      AuditoriaService auditoriaService) {
        this.usuarioService = usuarioService;
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
        this.notaRepository = notaRepository;
        this.devolucionService = devolucionService;
        this.sancionService = sancionService;
        this.cajaService = cajaService;
        this.auditoriaService = auditoriaService;
    }

    /** GET /api/crm/clientes/{id} - la Ficha 360 de un lector. */
    @Transactional(readOnly = true)
    public FichaClienteDTO ficha(Long idCliente) {
        Usuario cliente = buscarCliente(idCliente);

        List<Prestamo> prestamos = prestamoRepository.findByLectorIdOrderByFechaPrestamoDesc(idCliente);
        List<PrestamoResponseDTO> prestamosDTO = cajaService.prestamosConCajas(prestamos);
        List<Multa> multas = multaRepository.findByUsuarioIdOrderByFechaGeneracionDesc(idCliente);
        List<MultaResponseDTO> multasDTO = multas.stream().map(devolucionService::multaADTO).toList();
        List<CajaMovimientoDTO> pagos = cajaService.movimientosDeCliente(idCliente);
        List<SancionResponseDTO> sanciones = sancionService.sancionesDeUsuario(idCliente);
        List<NotaClienteDTO> notas = notaRepository.findByClienteIdOrderByFechaDesc(idCliente).stream()
                .map(CrmService::notaADTO).toList();

        // Devoluciones: cuántas fueron a tiempo y la fecha de la última.
        List<Devolucion> devoluciones = prestamos.isEmpty() ? List.of()
                : devolucionRepository.findByPrestamoIdIn(prestamos.stream().map(Prestamo::getId).toList());
        long devueltos = devoluciones.size();
        long aTiempo = devoluciones.stream().filter(d -> d.getDiasRetraso() == 0).count();

        long activos = prestamosDTO.stream().filter(p -> "ACTIVO".equals(p.estado()) || "VENCIDO".equals(p.estado())).count();
        long vencidos = prestamosDTO.stream().filter(p -> "VENCIDO".equals(p.estado())).count();
        long porCobrar = prestamosDTO.stream().filter(p -> "POR_COBRAR".equals(p.estado())).count();

        BigDecimal pagadoPrestamos = sumar(pagos, "COBRO_PRESTAMO_FECHA_FIJA", "COBRO_PRESTAMO_ABIERTO");
        BigDecimal pagadoMultas = sumar(pagos, "COBRO_MULTA");
        BigDecimal pagadoVirtual = sumar(pagos, "VENTA_VIRTUAL", "ALQUILER_VIRTUAL");
        BigDecimal totalPagado = pagadoPrestamos.add(pagadoMultas).add(pagadoVirtual);
        BigDecimal deuda = multas.stream()
                .filter(m -> m.getEstado() == EstadoMulta.PENDIENTE || m.getEstado() == EstadoMulta.PARCIAL)
                .map(Multa::getSaldo).reduce(CERO, BigDecimal::add);
        BigDecimal multasTotal = multas.stream().map(Multa::getMonto).reduce(CERO, BigDecimal::add);

        // Última actividad: el último préstamo, devolución o pago.
        List<LocalDateTime> fechas = new ArrayList<>();
        prestamos.forEach(p -> fechas.add(p.getFechaPrestamo()));
        devoluciones.forEach(d -> fechas.add(d.getFechaDevolucion()));
        pagos.forEach(m -> fechas.add(m.fecha()));
        LocalDateTime ultima = fechas.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        FichaClienteDTO.Resumen resumen = new FichaClienteDTO.Resumen(
                prestamos.size(), activos, vencidos, porCobrar, devueltos, aTiempo,
                devueltos == 0 ? null : (int) Math.round(aTiempo * 100.0 / devueltos),
                totalPagado, pagadoPrestamos, pagadoMultas, pagadoVirtual, deuda, multasTotal, ultima,
                sancionService.tieneSancionActiva(idCliente));

        return new FichaClienteDTO(usuarioService.aDTO(cliente), resumen, prestamosDTO, multasDTO, pagos,
                sanciones, notas);
    }

    /** POST /api/crm/clientes/{id}/notas - nota interna sobre el cliente. */
    @Transactional
    public NotaClienteDTO agregarNota(Long idCliente, NuevaNotaClienteDTO datos, Long idActor) {
        Usuario autor = usuarioService.buscarPersonal(idActor);
        Usuario cliente = buscarCliente(idCliente);
        String texto = datos.texto().trim();
        if (texto.isEmpty()) {
            throw new OperacionInvalidaException("Escribe la nota");
        }
        NotaCliente n = new NotaCliente();
        n.setCliente(cliente);
        n.setAutor(autor);
        n.setTexto(texto.length() > 1000 ? texto.substring(0, 1000) : texto);
        n.setFecha(LocalDateTime.now());
        n = notaRepository.save(n);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.NOTA_CLIENTE, "USUARIO", cliente.getId(),
                "Agregó una nota a la ficha de " + cliente.getNombreCompleto());
        return notaADTO(n);
    }

    /** DELETE /api/crm/notas/{id} - borrar una nota interna. */
    @Transactional
    public void borrarNota(Long idNota, Long idActor) {
        usuarioService.buscarPersonal(idActor);
        NotaCliente n = notaRepository.findById(idNota)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la nota con id " + idNota));
        Usuario cliente = n.getCliente();
        notaRepository.delete(n);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.NOTA_CLIENTE, "USUARIO", cliente.getId(),
                "Borró una nota de la ficha de " + cliente.getNombreCompleto());
    }

    private Usuario buscarCliente(Long idCliente) {
        Usuario u = usuarioService.buscar(idCliente);
        if (u.getRol() != Rol.LECTOR) {
            throw new OperacionInvalidaException("La Ficha 360 es solo para clientes (lectores)");
        }
        return u;
    }

    private static BigDecimal sumar(List<CajaMovimientoDTO> pagos, String... tipos) {
        List<String> lista = List.of(tipos);
        return pagos.stream().filter(m -> lista.contains(m.tipo()))
                .map(CajaMovimientoDTO::monto).reduce(CERO, BigDecimal::add);
    }

    private static NotaClienteDTO notaADTO(NotaCliente n) {
        return new NotaClienteDTO(n.getId(), n.getTexto(), n.getFecha(), n.getAutor().getNombreCompleto());
    }
}
