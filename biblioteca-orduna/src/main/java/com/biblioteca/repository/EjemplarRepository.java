package com.biblioteca.repository;

import com.biblioteca.entity.Ejemplar;
import com.biblioteca.entity.EstadoEjemplar;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EjemplarRepository extends JpaRepository<Ejemplar, Long> {
    boolean existsByCodigoInventario(String codigoInventario);
    long countByLibroIdAndEstado(Long idLibro, EstadoEjemplar estado);
    long countByLibroId(Long idLibro);
    long countByEstado(EstadoEjemplar estado);

    /**
     * Total de copias y copias disponibles de varios libros en UNA consulta
     * (el catalogo no hace dos consultas por libro). Filas: [idLibro, total, disponibles].
     */
    @Query("""
            SELECT e.libro.id, COUNT(e), SUM(CASE WHEN e.estado = :disponible THEN 1 ELSE 0 END)
            FROM Ejemplar e
            WHERE e.libro.id IN :ids
            GROUP BY e.libro.id
            """)
    List<Object[]> contarPorLibros(@Param("ids") Collection<Long> ids,
                                   @Param("disponible") EstadoEjemplar disponible);

    /**
     * Primer ejemplar disponible de un libro (préstamo eligiendo solo el libro).
     * Bloqueo pesimista de escritura: dos préstamos simultáneos del mismo libro se
     * serializan aquí, así que el segundo ve el ejemplar ya PRESTADO y lo rechaza
     * en vez de registrar el mismo ejemplar dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ejemplar> findFirstByLibroIdAndEstadoOrderByIdAsc(Long idLibro, EstadoEjemplar estado);

    /** Igual que el anterior, pero para un ejemplar concreto pedido en el cuerpo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Ejemplar e WHERE e.id = :idEjemplar")
    Optional<Ejemplar> findByIdForUpdate(@Param("idEjemplar") Long idEjemplar);

    /** Pantalla Ejemplares: filtros opcionales por libro, estado y texto (código, ubicación o título). */
    @Query("""
            SELECT e FROM Ejemplar e JOIN e.libro l
            WHERE (:idLibro IS NULL OR l.id = :idLibro)
              AND (:estado IS NULL OR e.estado = :estado)
              AND (:q = ''
                   OR LOWER(e.codigoInventario) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.ubicacion) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY l.titulo, e.codigoInventario
            """)
    List<Ejemplar> buscar(@Param("idLibro") Long idLibro, @Param("estado") EstadoEjemplar estado,
                          @Param("q") String q);
}
