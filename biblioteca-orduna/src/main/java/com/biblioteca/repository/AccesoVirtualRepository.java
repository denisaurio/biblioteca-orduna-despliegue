package com.biblioteca.repository;

import com.biblioteca.entity.AccesoVirtual;
import com.biblioteca.entity.EstadoAccesoVirtual;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccesoVirtualRepository extends JpaRepository<AccesoVirtual, Long> {

    /** El codigo que el cliente muestra en el mostrador. */
    Optional<AccesoVirtual> findByCodigoRetiro(String codigoRetiro);

    boolean existsByCodigoRetiro(String codigoRetiro);

    /** Libros del lector (perfil), del mas reciente al mas antiguo. */
    List<AccesoVirtual> findByUsuarioIdOrderByFechaCreacionDesc(Long idUsuario);

    /** Historial de un lector sobre un libro concreto. */
    List<AccesoVirtual> findByUsuarioIdAndLibroIdOrderByFechaCreacionDesc(Long idUsuario, Long idLibro);

    /** Pedidos que aun no se han cobrado (los que ve el personal en el panel). */
    List<AccesoVirtual> findByEstadoOrderByFechaCreacionAsc(EstadoAccesoVirtual estado);

    /** Pedidos de un libro, del mas nuevo al mas viejo. */
    List<AccesoVirtual> findByLibroIdOrderByFechaCreacionDesc(Long idLibro);

    /** Pedidos pendientes cuyo codigo de 24 horas ya vencio. */
    List<AccesoVirtual> findByEstadoAndFechaCodigoExpiraBefore(EstadoAccesoVirtual estado, LocalDateTime limite);

    /**
     * Pedido bloqueado mientras se cobra: si dos personas pulsan a la vez, el segundo
     * ve que ya esta pagado y no lo activa dos veces (ni cobra dos veces).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccesoVirtual a WHERE a.id = :idAcceso")
    Optional<AccesoVirtual> findByIdForUpdate(@Param("idAcceso") Long idAcceso);
}
