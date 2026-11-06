package com.biblioteca.repository;

import com.biblioteca.entity.EstadoMulta;
import com.biblioteca.entity.Multa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MultaRepository extends JpaRepository<Multa, Long> {
    /** true si el usuario debe algo (multa PENDIENTE o PARCIAL). */
    boolean existsByUsuarioIdAndEstadoIn(Long idUsuario, Collection<EstadoMulta> estados);

    /**
     * Multa bloqueada para cobrar. Dos cajeros que cobran a la vez se serializan,
     * así el segundo ve el saldo ya reducido y no puede cobrar de más.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Multa m WHERE m.id = :idMulta")
    Optional<Multa> findByIdForUpdate(@Param("idMulta") Long idMulta);

    List<Multa> findByEstadoIn(Collection<EstadoMulta> estados);

    List<Multa> findByEstadoInOrderByFechaGeneracionDesc(Collection<EstadoMulta> estados);

    List<Multa> findAllByOrderByFechaGeneracionDesc();

    List<Multa> findByUsuarioIdOrderByFechaGeneracionDesc(Long idUsuario);

    List<Multa> findByFechaGeneracionBetweenOrderByFechaGeneracionAsc(LocalDateTime desde, LocalDateTime hasta);
}
