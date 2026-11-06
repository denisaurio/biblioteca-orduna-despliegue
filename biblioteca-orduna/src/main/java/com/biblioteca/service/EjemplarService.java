package com.biblioteca.service;

import com.biblioteca.dto.ActualizarEjemplarDTO;
import com.biblioteca.dto.EjemplarResponseDTO;
import com.biblioteca.dto.RegistroEjemplarDTO;
import com.biblioteca.entity.Ejemplar;
import com.biblioteca.entity.EstadoEjemplar;
import com.biblioteca.entity.Libro;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.TipoLibro;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoDuplicadoException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.EjemplarRepository;
import com.biblioteca.repository.LibroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

/** RF14 - Ejemplares físicos (registrar, editar, dar de baja, consultar). */
@Service
public class EjemplarService {

    /** Estados que el personal puede poner a mano. PRESTADO y RESERVADO los maneja el sistema. */
    private static final EnumSet<EstadoEjemplar> MANUALES =
            EnumSet.of(EstadoEjemplar.DISPONIBLE, EstadoEjemplar.DANADO, EstadoEjemplar.PERDIDO, EstadoEjemplar.BAJA);

    private final EjemplarRepository ejemplarRepository;
    private final LibroRepository libroRepository;
    private final ReservaService reservaService;
    private final AuditoriaService auditoriaService;

    public EjemplarService(EjemplarRepository ejemplarRepository, LibroRepository libroRepository,
                           ReservaService reservaService, AuditoriaService auditoriaService) {
        this.ejemplarRepository = ejemplarRepository;
        this.libroRepository = libroRepository;
        this.reservaService = reservaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * RF14 - Registrar ejemplar. RecursoDuplicadoException si el código ya existe.
     * Si hay lectores esperando ese libro, el ejemplar nuevo se aparta para el primero de la cola.
     */
    @Transactional
    public EjemplarResponseDTO registrarEjemplar(Long idLibro, RegistroEjemplarDTO datos, Long idActor) {
        Libro libro = libroRepository.findById(idLibro)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el libro con id " + idLibro));
        if (libro.getTipoLibro() == TipoLibro.VIRTUAL) {
            throw new OperacionInvalidaException(
                    "Un libro virtual no tiene ejemplares: sus copias son el PDF");
        }
        String codigo = datos.codigoInventario().trim().toUpperCase();
        if (ejemplarRepository.existsByCodigoInventario(codigo)) {
            throw new RecursoDuplicadoException("Ya existe un ejemplar con el código de inventario " + codigo);
        }
        Ejemplar e = new Ejemplar();
        e.setLibro(libro);
        e.setCodigoInventario(codigo);
        e.setUbicacion(datos.ubicacion());
        e.setPrecio(precioDe(datos.precio(), libro));
        e.setEstado(EstadoEjemplar.DISPONIBLE);
        e = ejemplarRepository.save(e);
        reservaService.asignarEjemplarLiberado(e);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.REGISTRAR_EJEMPLAR, "EJEMPLAR", e.getId(),
                "Registró el ejemplar " + codigo + " de '" + libro.getTitulo() + "'");
        return aDTO(e);
    }

    /** Pantalla Ejemplares: filtros opcionales por libro, estado y texto. */
    @Transactional(readOnly = true)
    public List<EjemplarResponseDTO> listarEjemplares(Long idLibro, EstadoEjemplar estado, String q) {
        return ejemplarRepository.buscar(idLibro, estado, q == null ? "" : q.trim()).stream()
                .map(EjemplarService::aDTO).toList();
    }

    @Transactional(readOnly = true)
    public EjemplarResponseDTO obtenerEjemplar(Long idEjemplar) {
        return aDTO(buscar(idEjemplar));
    }

    /**
     * Editar ubicación y/o estado (DISPONIBLE, DANADO, PERDIDO, BAJA).
     * No se puede cambiar el estado de un ejemplar PRESTADO o RESERVADO.
     * Si vuelve a DISPONIBLE (ej. reparado), se ofrece a la cola de reservas.
     */
    @Transactional
    public EjemplarResponseDTO actualizarEjemplar(Long idEjemplar, ActualizarEjemplarDTO datos, Long idActor) {
        Ejemplar e = buscar(idEjemplar);
        if (datos.ubicacion() != null) {
            e.setUbicacion(datos.ubicacion().isBlank() ? null : datos.ubicacion().trim());
        }
        if (datos.precio() != null) {
            e.setPrecio(precioDe(datos.precio(), e.getLibro()));
        }
        EstadoEjemplar nuevo = datos.estado();
        if (nuevo != null && nuevo != e.getEstado()) {
            if (!MANUALES.contains(nuevo)) {
                throw new OperacionInvalidaException("Estado no permitido: use DISPONIBLE, DANADO, PERDIDO o BAJA");
            }
            if (e.getEstado() == EstadoEjemplar.PRESTADO || e.getEstado() == EstadoEjemplar.RESERVADO) {
                throw new OperacionInvalidaException("El ejemplar está " + e.getEstado().name().toLowerCase()
                        + "; su estado cambia al registrar la devolución o atender la reserva");
            }
            if (nuevo == EstadoEjemplar.DISPONIBLE) {
                reservaService.asignarEjemplarLiberado(e);
            } else {
                e.setEstado(nuevo);
            }
        }
        e = ejemplarRepository.save(e);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.EDITAR_EJEMPLAR, "EJEMPLAR", e.getId(),
                "Actualizó el ejemplar " + e.getCodigoInventario() + " de '" + e.getLibro().getTitulo()
                        + "' (estado: " + e.getEstado().name() + ")");
        return aDTO(e);
    }

    private Ejemplar buscar(Long idEjemplar) {
        return ejemplarRepository.findById(idEjemplar)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el ejemplar con id " + idEjemplar));
    }

    /**
     * El precio de la copia. Si no se indica, se usa el precio general del libro:
     * todas las copias valen lo mismo salvo que se indique otra cosa.
     */
    static BigDecimal precioDe(BigDecimal precioPropio, Libro libro) {
        if (precioPropio != null) return precioPropio;
        return libro == null ? null : libro.getPrecioCompra();
    }

    static EjemplarResponseDTO aDTO(Ejemplar e) {
        return new EjemplarResponseDTO(e.getId(), e.getLibro().getId(), e.getLibro().getTitulo(),
                e.getCodigoInventario(), e.getUbicacion(), e.getEstado().name(),
                precioDe(e.getPrecio(), e.getLibro()), e.getLibro().getPrecioCompra());
    }
}
