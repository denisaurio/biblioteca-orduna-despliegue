package com.biblioteca.service;

import com.biblioteca.dto.AuditoriaResponseDTO;
import com.biblioteca.dto.PaginaDTO;
import com.biblioteca.entity.Auditoria;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.Usuario;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.AuditoriaRepository;
import com.biblioteca.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Auditoría: quién hizo qué en la biblioteca.
 *
 * Se llama explícitamente desde los servicios (no con un interceptor): así cada registro
 * lleva una descripción legible y no se duplica nada.
 *
 * La auditoría va EN LA MISMA TRANSACCIÓN que la operación: si la operación se revierte,
 * la auditoría tampoco se guarda. Por eso el método que la llama no puede ser de solo
 * lectura (una transacción readOnly no deja escribir).
 *
 * Solo el gerente la ve: el endpoint es /api/auditoria y está cerrado en SecurityConfig.
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, UsuarioRepository usuarioRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Anota una acción.
     *
     * @param idActor quién la hizo (null si fue una acción pública sin sesión)
     * @param accion   qué hizo
     * @param entidad  sobre qué (LIBRO, PRESTAMO, CAJA...)
     * @param entidadId el id de esa entidad
     * @param descripcion la frase legible que ve el gerente
     */
    @Transactional
    public void registrar(Long idActor, TipoAccionAuditoria accion, String entidad, Long entidadId,
                          String descripcion) {
        Usuario actor = idActor == null ? null : usuarioRepository.findById(idActor)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + idActor));
        Auditoria a = new Auditoria();
        a.setUsuario(actor);
        a.setAccion(accion);
        a.setEntidad(recortar(entidad));
        a.setEntidadId(entidadId);
        a.setDescripcion(recortar(descripcion));
        a.setFecha(LocalDateTime.now());
        auditoriaRepository.save(a);
    }

    /** La auditoría con filtros: fechas, usuario y tipo de acción. Paginado. */
    @Transactional(readOnly = true)
    public PaginaDTO<AuditoriaResponseDTO> listar(LocalDate desde, LocalDate hasta, Long idUsuario,
                                                  TipoAccionAuditoria accion, Integer pagina, Integer tamanio) {
        LocalDate[] r = ReporteService.rango(desde, hasta);
        return Paginas.aDTO(
                auditoriaRepository.buscar(r[0].atStartOfDay(), r[1].atTime(LocalTime.MAX),
                        idUsuario, accion, Paginas.de(pagina, tamanio)),
                AuditoriaService::aDTO);
    }

    private static AuditoriaResponseDTO aDTO(Auditoria a) {
        Usuario u = a.getUsuario();
        return new AuditoriaResponseDTO(a.getId(), a.getFecha(),
                u == null ? null : u.getId(),
                u == null ? null : u.getNombreCompleto(),
                u == null ? null : u.getRol().name(),
                a.getAccion().name(), a.getEntidad(), a.getEntidadId(), a.getDescripcion());
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        if (t.isEmpty()) return null;
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
