package com.biblioteca.repository;

import com.biblioteca.entity.Auditoria;
import com.biblioteca.entity.TipoAccionAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Consulta de la auditoría (pantalla del gerente).
 * Todas las consultas van parametrizadas: no se concatena nada.
 */
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /**
     * Filtros: rango de fechas, usuario y tipo de acción (los que llegan en null no filtran).
     * Las fechas SIEMPRE llegan concretas (el servicio las acota), así que aquí no hace
     * falta comparar con null y la consulta es más simple.
     */
    @Query("""
            SELECT a FROM Auditoria a
            WHERE a.fecha BETWEEN :desde AND :hasta
              AND (:idUsuario IS NULL OR a.usuario.id = :idUsuario)
              AND (:accion IS NULL OR a.accion = :accion)
            """)
    Page<Auditoria> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                           @Param("idUsuario") Long idUsuario, @Param("accion") TipoAccionAuditoria accion,
                           Pageable pageable);
}
