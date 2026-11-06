package com.biblioteca.repository;

import com.biblioteca.entity.CajaMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CajaMovimientoRepository extends JpaRepository<CajaMovimiento, Long> {

    /** Movimientos de una caja, del más antiguo al más reciente. */
    List<CajaMovimiento> findByCajaIdOrderByFechaAsc(Long idCaja);

    /** Movimientos de un rango de fechas (el total del día). */
    List<CajaMovimiento> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);

    /** Total cobrado en una caja (todos los métodos de pago). */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimiento m WHERE m.caja.id = :idCaja")
    BigDecimal sumarPorCaja(@Param("idCaja") Long idCaja);

    /** Efectivo recibido en una caja (lo que está en el cajón). */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimiento m "
            + "WHERE m.caja.id = :idCaja AND m.metodoPago = com.biblioteca.entity.MetodoPago.EFECTIVO")
    BigDecimal sumarEfectivoPorCaja(@Param("idCaja") Long idCaja);

    /** Total cobrado en un rango de fechas, de todas las cajas. */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM CajaMovimiento m WHERE m.fecha BETWEEN :desde AND :hasta")
    BigDecimal sumarPorRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Desglose por método de pago: [metodo, total]. */
    @Query("SELECT m.metodoPago, COALESCE(SUM(m.monto), 0) FROM CajaMovimiento m "
            + "WHERE m.fecha BETWEEN :desde AND :hasta GROUP BY m.metodoPago")
    List<Object[]> totalPorMetodoEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Desglose por tipo de movimiento: [tipo, total]. */
    @Query("SELECT m.tipo, COALESCE(SUM(m.monto), 0) FROM CajaMovimiento m "
            + "WHERE m.fecha BETWEEN :desde AND :hasta GROUP BY m.tipo")
    List<Object[]> totalPorTipoEnRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
