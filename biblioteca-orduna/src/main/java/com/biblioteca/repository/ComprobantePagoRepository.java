package com.biblioteca.repository;

import com.biblioteca.entity.ComprobantePago;
import com.biblioteca.entity.EstadoComprobante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, Long> {

    List<ComprobantePago> findByEstadoOrderByFechaEnvioAsc(EstadoComprobante estado);

    List<ComprobantePago> findByAccesoIdOrderByFechaEnvioDesc(Long idAcceso);

    /** Comprobantes de varios pedidos de una vez (para pintar "en revision" en el perfil). */
    List<ComprobantePago> findByAccesoIdInOrderByFechaEnvioDesc(List<Long> idsAcceso);

    /** El ULTIMO comprobante del pedido, este en el estado que sea (para el perfil del lector). */
    Optional<ComprobantePago> findFirstByAccesoIdOrderByFechaEnvioDesc(Long idAcceso);

    long countByEstado(EstadoComprobante estado);

    /**
     * Comprobante bloqueado mientras se revisa: dos personas que pulsan "Aceptar"
     * a la vez se serializan, asi el segundo ve que ya estaba aprobado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ComprobantePago c WHERE c.id = :idComprobante")
    Optional<ComprobantePago> findByIdForUpdate(@Param("idComprobante") Long idComprobante);
}
