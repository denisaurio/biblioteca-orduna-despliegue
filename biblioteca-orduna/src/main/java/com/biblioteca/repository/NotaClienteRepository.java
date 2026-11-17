package com.biblioteca.repository;

import com.biblioteca.entity.NotaCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaClienteRepository extends JpaRepository<NotaCliente, Long> {

    /** Notas de un cliente, la más reciente primero. */
    List<NotaCliente> findByClienteIdOrderByFechaDesc(Long idCliente);
}
