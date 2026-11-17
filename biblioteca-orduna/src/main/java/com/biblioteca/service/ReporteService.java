package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF10 - Reportes estadísticos de inventario, préstamos y multas.
 * Cada reporte se puede pedir como JSON (para pantallas/gráficos) o exportar a CSV
 * (se abre directo en Excel: separador ';' y UTF-8 con BOM para las tildes).
 */
@Service
public class ReporteService {

    private static final int TOP = 5;
    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LibroRepository libroRepository;
    private final EjemplarRepository ejemplarRepository;
    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;
    private final PagoMultaRepository pagoMultaRepository;

    public ReporteService(LibroRepository libroRepository, EjemplarRepository ejemplarRepository,
                          PrestamoRepository prestamoRepository, DevolucionRepository devolucionRepository,
                          MultaRepository multaRepository, PagoMultaRepository pagoMultaRepository) {
        this.libroRepository = libroRepository;
        this.ejemplarRepository = ejemplarRepository;
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
        this.pagoMultaRepository = pagoMultaRepository;
    }

    // ===================== INVENTARIO =====================

    /** GET /api/reportes/inventario */
    @Transactional(readOnly = true)
    public ReporteInventarioDTO reporteInventario() {
        List<Ejemplar> ejemplares = ejemplarRepository.findAll();
        Map<Long, List<Ejemplar>> porLibro = ejemplares.stream()
                .collect(Collectors.groupingBy(e -> e.getLibro().getId()));

        List<FilaInventarioDTO> detalle = libroRepository.findAll().stream()
                .sorted(Comparator.comparing(Libro::getTitulo, String.CASE_INSENSITIVE_ORDER))
                .map(l -> {
                    List<Ejemplar> es = porLibro.getOrDefault(l.getId(), List.of());
                    return new FilaInventarioDTO(l.getId(), l.getTitulo(), l.getIsbn(),
                            unir(l.getAutores().stream().map(Autor::getNombre)),
                            unir(l.getGeneros().stream().map(Genero::getNombre)),
                            es.size(), contar(es, EstadoEjemplar.DISPONIBLE), contar(es, EstadoEjemplar.PRESTADO),
                            contar(es, EstadoEjemplar.RESERVADO), contar(es, EstadoEjemplar.DANADO),
                            contar(es, EstadoEjemplar.PERDIDO), contar(es, EstadoEjemplar.BAJA));
                }).toList();

        long prestados = contar(ejemplares, EstadoEjemplar.PRESTADO);
        long danados = contar(ejemplares, EstadoEjemplar.DANADO);
        long perdidos = contar(ejemplares, EstadoEjemplar.PERDIDO);
        long baja = contar(ejemplares, EstadoEjemplar.BAJA);
        return new ReporteInventarioDTO(LocalDateTime.now(), detalle.size(), ejemplares.size(),
                contar(ejemplares, EstadoEjemplar.DISPONIBLE), prestados,
                contar(ejemplares, EstadoEjemplar.RESERVADO), danados, perdidos, baja,
                porcentaje(prestados, ejemplares.size() - danados - perdidos - baja), detalle);
    }

    /** GET /api/reportes/inventario/csv */
    @Transactional(readOnly = true)
    public byte[] exportarInventarioCsv() {
        ReporteInventarioDTO r = reporteInventario();
        Csv csv = new Csv();
        csv.fila("REPORTE DE INVENTARIO");
        csv.fila("Generado", r.fechaGeneracion().format(F_FECHA_HORA));
        csv.fila("Total libros", r.totalLibros());
        csv.fila("Total ejemplares", r.totalEjemplares());
        csv.fila("Disponibles", r.disponibles());
        csv.fila("Prestados", r.prestados());
        csv.fila("Reservados", r.reservados());
        csv.fila("Dañados", r.danados());
        csv.fila("Perdidos", r.perdidos());
        csv.fila("De baja", r.deBaja());
        csv.fila("% en uso", r.porcentajeEnUso());
        csv.fila();
        csv.fila("ID", "Título", "ISBN", "Autores", "Géneros", "Total", "Disponibles", "Prestados", "Reservados",
                "Dañados", "Perdidos", "De baja");
        for (FilaInventarioDTO f : r.detalle()) {
            csv.fila(f.idLibro(), f.titulo(), f.isbn(), f.autores(), f.generos(), f.total(),
                    f.disponibles(), f.prestados(), f.reservados(), f.danados(), f.perdidos(), f.deBaja());
        }
        return csv.bytes();
    }

    // ===================== PRÉSTAMOS =====================

    /** GET /api/reportes/prestamos?desde=2026-10-01&hasta=2026-10-31 (null = mes actual) */
    @Transactional(readOnly = true)
    public ReportePrestamosDTO reportePrestamos(LocalDate desde, LocalDate hasta) {
        LocalDate[] rango = rango(desde, hasta);
        LocalDate hoy = LocalDate.now();
        List<Prestamo> prestamos = prestamoRepository.findByFechaPrestamoBetweenOrderByFechaPrestamoAsc(
                rango[0].atStartOfDay(), rango[1].atTime(LocalTime.MAX));
        Map<Long, Devolucion> devPorPrestamo = prestamos.isEmpty() ? Map.of()
                : devolucionRepository.findByPrestamoIdIn(prestamos.stream().map(Prestamo::getId).toList())
                .stream().collect(Collectors.toMap(d -> d.getPrestamo().getId(), Function.identity()));

        List<FilaPrestamoDTO> detalle = new ArrayList<>();
        long activos = 0, vencidos = 0, devueltos = 0, conRetraso = 0;
        for (Prestamo p : prestamos) {
            // POR_COBRAR y ANULADO: el libro no salió, no cuentan como préstamos del reporte.
            if (p.getEstado() == EstadoPrestamo.POR_COBRAR || p.getEstado() == EstadoPrestamo.ANULADO) {
                continue;
            }
            Devolucion d = devPorPrestamo.get(p.getId());
            String estado;
            if (p.getEstado() == EstadoPrestamo.DEVUELTO) {
                devueltos++;
                if (d != null && d.getDiasRetraso() > 0) conRetraso++;
                estado = "DEVUELTO";
            } else if (p.estaVencido(hoy)) {
                vencidos++;
                estado = "VENCIDO";
            } else {
                activos++;
                estado = "ACTIVO";
            }
            int diasRetraso = d != null ? d.getDiasRetraso()
                    : DevolucionService.calcularDiasRetraso(p.getFechaVencimiento(), hoy);
            detalle.add(new FilaPrestamoDTO(p.getId(), p.getTipo().name(), p.getFechaPrestamo(), p.getFechaVencimiento(),
                    d == null ? null : d.getFechaDevolucion(), diasRetraso,
                    p.getEjemplar().getLibro().getTitulo(), p.getEjemplar().getCodigoInventario(),
                    p.getLector().getNombreCompleto(), p.getLector().getDni(), estado));
        }

        return new ReportePrestamosDTO(rango[0], rango[1], prestamos.size(), activos, vencidos, devueltos,
                conRetraso, porcentaje(conRetraso, devueltos),
                top(prestamos, p -> p.getEjemplar().getLibro().getTitulo()),
                top(prestamos, p -> p.getLector().getNombreCompleto() + " (" + p.getLector().getDni() + ")"),
                detalle);
    }

    /** GET /api/reportes/prestamos/csv?desde=&hasta= */
    @Transactional(readOnly = true)
    public byte[] exportarPrestamosCsv(LocalDate desde, LocalDate hasta) {
        ReportePrestamosDTO r = reportePrestamos(desde, hasta);
        Csv csv = new Csv();
        csv.fila("REPORTE DE PRÉSTAMOS");
        csv.fila("Desde", r.desde().format(F_FECHA), "Hasta", r.hasta().format(F_FECHA));
        csv.fila("Total préstamos", r.totalPrestamos());
        csv.fila("Activos", r.activos());
        csv.fila("Vencidos sin devolver", r.vencidosSinDevolver());
        csv.fila("Devueltos", r.devueltos());
        csv.fila("Devueltos con retraso", r.devueltosConRetraso());
        csv.fila("% con retraso", r.porcentajeRetraso());
        csv.fila();
        csv.fila("Libros más prestados", "Cantidad");
        r.librosMasPrestados().forEach(x -> csv.fila(x.nombre(), x.cantidad()));
        csv.fila();
        csv.fila("Lectores más activos", "Cantidad");
        r.lectoresMasActivos().forEach(x -> csv.fila(x.nombre(), x.cantidad()));
        csv.fila();
        csv.fila("ID", "Tipo", "Fecha préstamo", "Vencimiento", "Fecha devolución", "Días retraso", "Libro",
                "Ejemplar", "Lector", "DNI", "Estado");
        for (FilaPrestamoDTO f : r.detalle()) {
            csv.fila(f.id(), f.tipoPrestamo(), f.fechaPrestamo().format(F_FECHA_HORA),
                    f.fechaVencimiento() == null ? "Abierto" : f.fechaVencimiento().format(F_FECHA),
                    f.fechaDevolucion() == null ? "" : f.fechaDevolucion().format(F_FECHA_HORA), f.diasRetraso(),
                    f.tituloLibro(), f.codigoInventario(), f.lector(), f.dni(), f.estado());
        }
        return csv.bytes();
    }

    // ===================== MULTAS =====================

    /** GET /api/reportes/multas?desde=&hasta= (null = mes actual). Considera pagos parciales. */
    @Transactional(readOnly = true)
    public ReporteMultasDTO reporteMultas(LocalDate desde, LocalDate hasta) {
        LocalDate[] rango = rango(desde, hasta);
        List<Multa> multas = multaRepository.findByFechaGeneracionBetweenOrderByFechaGeneracionAsc(
                rango[0].atStartOfDay(), rango[1].atTime(LocalTime.MAX));
        Map<Long, List<PagoMulta>> pagosPorMulta = multas.isEmpty() ? Map.of()
                : pagoMultaRepository.findByMultaIdIn(multas.stream().map(Multa::getId).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getMulta().getId()));

        BigDecimal total = BigDecimal.ZERO, cobrado = BigDecimal.ZERO;
        long pagadas = 0, parciales = 0;
        Map<String, BigDecimal> porMetodo = new TreeMap<>();
        List<FilaMultaDTO> detalle = new ArrayList<>();
        for (Multa m : multas) {
            total = total.add(m.getMonto());
            if (m.getEstado() == EstadoMulta.PAGADA) pagadas++;
            if (m.getEstado() == EstadoMulta.PARCIAL) parciales++;
            List<PagoMulta> pagos = pagosPorMulta.getOrDefault(m.getId(), List.of());
            for (PagoMulta pago : pagos) {
                cobrado = cobrado.add(pago.getMontoPagado());
                porMetodo.merge(pago.getMetodoPago().name(), pago.getMontoPagado(), BigDecimal::add);
            }
            LocalDateTime ultimoPago = pagos.stream().map(PagoMulta::getFechaPago)
                    .max(Comparator.naturalOrder()).orElse(null);
            String metodos = pagos.stream().map(x -> x.getMetodoPago().name()).distinct()
                    .collect(Collectors.joining(", "));
            Prestamo p = m.getDevolucion().getPrestamo();
            detalle.add(new FilaMultaDTO(m.getId(), m.getFechaGeneracion(), m.getUsuario().getNombreCompleto(),
                    m.getUsuario().getDni(), p.getEjemplar().getLibro().getTitulo(),
                    m.getDevolucion().getDiasRetraso(), m.getMonto(), m.getMontoPagado(), m.getEstado().name(),
                    ultimoPago, metodos.isEmpty() ? null : metodos));
        }
        return new ReporteMultasDTO(rango[0], rango[1], multas.size(), pagadas, parciales,
                multas.size() - pagadas - parciales, total, cobrado, total.subtract(cobrado), porMetodo, detalle);
    }

    /** GET /api/reportes/multas/csv?desde=&hasta= */
    @Transactional(readOnly = true)
    public byte[] exportarMultasCsv(LocalDate desde, LocalDate hasta) {
        ReporteMultasDTO r = reporteMultas(desde, hasta);
        Csv csv = new Csv();
        csv.fila("REPORTE DE MULTAS");
        csv.fila("Desde", r.desde().format(F_FECHA), "Hasta", r.hasta().format(F_FECHA));
        csv.fila("Total multas", r.totalMultas());
        csv.fila("Pagadas", r.pagadas());
        csv.fila("Pago parcial", r.parciales());
        csv.fila("Pendientes", r.pendientes());
        csv.fila("Monto total (S/)", r.montoTotal());
        csv.fila("Monto cobrado (S/)", r.montoCobrado());
        csv.fila("Monto pendiente (S/)", r.montoPendiente());
        r.cobradoPorMetodo().forEach((k, v) -> csv.fila("Cobrado con " + k + " (S/)", v));
        csv.fila();
        csv.fila("ID", "Fecha", "Lector", "DNI", "Libro", "Días retraso", "Monto (S/)", "Pagado (S/)", "Estado",
                "Último pago", "Métodos de pago");
        for (FilaMultaDTO f : r.detalle()) {
            csv.fila(f.id(), f.fechaGeneracion().format(F_FECHA_HORA), f.lector(), f.dni(), f.tituloLibro(),
                    f.diasRetraso(), f.monto(), f.montoPagado(), f.estado(),
                    f.fechaUltimoPago() == null ? "" : f.fechaUltimoPago().format(F_FECHA_HORA),
                    f.metodosPago() == null ? "" : f.metodosPago());
        }
        return csv.bytes();
    }

    // ===================== Ayudantes =====================

    /** Si no mandan fechas: desde el día 1 del mes actual hasta hoy. */
    static LocalDate[] rango(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        LocalDate d = desde != null ? desde : h.withDayOfMonth(1);
        if (d.isAfter(h)) {
            throw new OperacionInvalidaException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        return new LocalDate[]{d, h};
    }

    private static long contar(List<Ejemplar> es, EstadoEjemplar estado) {
        return es.stream().filter(e -> e.getEstado() == estado).count();
    }

    private static double porcentaje(long parte, long total) {
        if (total <= 0) return 0.0;
        return BigDecimal.valueOf(parte * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static <T> List<RankingDTO> top(List<T> lista, Function<T, String> clave) {
        return lista.stream().collect(Collectors.groupingBy(clave, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP)
                .map(e -> new RankingDTO(e.getKey(), e.getValue()))
                .toList();
    }

    private static String unir(java.util.stream.Stream<String> nombres) {
        return nombres.sorted().collect(Collectors.joining(", "));
    }

    /** CSV compatible con Excel en español (separador ';', UTF-8 con BOM). */
    static final class Csv {
        private final StringBuilder sb = new StringBuilder("﻿");

        void fila(Object... valores) {
            sb.append(Arrays.stream(valores).map(Csv::celda).collect(Collectors.joining(";"))).append("\r\n");
        }

        private static String celda(Object v) {
            if (v == null) return "";
            String s = v instanceof BigDecimal b ? b.toPlainString() : String.valueOf(v);
            if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }

        byte[] bytes() {
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
