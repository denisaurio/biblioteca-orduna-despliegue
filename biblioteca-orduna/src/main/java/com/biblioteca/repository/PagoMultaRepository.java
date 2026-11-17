package com.biblioteca.repository;

import com.biblioteca.entity.PagoMulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PagoMultaRepository extends JpaRepository<PagoMulta, Long> {
    List<PagoMulta> findByFechaPagoBetween(LocalDateTime desde, LocalDateTime hasta);

    List<PagoMulta> findByFechaPagoBetweenOrderByFechaPagoDesc(LocalDateTime desde, LocalDateTime hasta);

    List<PagoMulta> findByMultaIdIn(Collection<Long> idsMulta);

    List<PagoMulta> findByMultaIdOrderByFechaPagoAsc(Long idMulta);

    /** Pagos de multa de un cliente (Ficha 360). */
    List<PagoMulta> findByMultaUsuarioIdOrderByFechaPagoDesc(Long idUsuario);
}
