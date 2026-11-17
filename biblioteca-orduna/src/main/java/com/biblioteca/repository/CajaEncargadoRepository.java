package com.biblioteca.repository;

import com.biblioteca.entity.CajaEncargado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CajaEncargadoRepository extends JpaRepository<CajaEncargado, Long> {

    /** Historial de encargados de una caja, del primero al último. */
    List<CajaEncargado> findByCajaIdOrderByDesdeAsc(Long idCaja);

    /** El tramo del encargado actual (sin "hasta"). */
    Optional<CajaEncargado> findFirstByCajaIdAndHastaIsNullOrderByDesdeDesc(Long idCaja);
}
