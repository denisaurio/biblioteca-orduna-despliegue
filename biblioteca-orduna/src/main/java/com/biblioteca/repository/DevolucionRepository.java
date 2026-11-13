package com.biblioteca.repository;

import com.biblioteca.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {
    boolean existsByPrestamoId(Long idPrestamo);

    long countByFechaDevolucionBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Devolucion> findByPrestamoIdIn(Collection<Long> idsPrestamo);

    /** Devoluciones a las que todavía se les puede generar una multa (no tienen ninguna). */
    @Query("SELECT d FROM Devolucion d WHERE NOT EXISTS "
            + "(SELECT m FROM Multa m WHERE m.devolucion.id = d.id) "
            + "ORDER BY d.fechaDevolucion DESC")
    List<Devolucion> findSinMulta();
}
