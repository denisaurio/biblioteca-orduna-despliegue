package com.biblioteca.repository;

import com.biblioteca.entity.Caja;
import com.biblioteca.entity.EstadoCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Long> {

    /** La caja abierta (caja_abierta = 1). */
    Optional<Caja> findByCajaAbierta(Integer cajaAbierta);

    /**
     * La última caja con ese estado, bloqueada.
     * El bloqueo evita que dos personas abran una caja a la vez: la segunda ve la
     * que ya está abierta y no la deja pasar. El índice UNIQUE de "caja_abierta"
     * es la garantía final (la base no deja dos cajas abiertas).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Caja c WHERE c.estado = :estado ORDER BY c.fechaApertura DESC")
    List<Caja> findByEstadoOrderByFechaAperturaDescForUpdate(@Param("estado") EstadoCaja estado);

    /** Caja bloqueada mientras se cierra (dos personas no cierran la misma caja). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Caja c WHERE c.id = :idCaja")
    Optional<Caja> findByIdForUpdate(@Param("idCaja") Long idCaja);

    /** Historial: las cajas abiertas en un rango de fechas. */
    List<Caja> findByFechaAperturaBetweenOrderByFechaAperturaDesc(LocalDateTime desde, LocalDateTime hasta);

    List<Caja> findAllByOrderByFechaAperturaDesc();
}
