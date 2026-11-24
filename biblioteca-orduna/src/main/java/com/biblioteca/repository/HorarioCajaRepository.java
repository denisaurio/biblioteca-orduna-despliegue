package com.biblioteca.repository;

import com.biblioteca.entity.HorarioCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface HorarioCajaRepository extends JpaRepository<HorarioCaja, Long> {

    /** El horario de hoy (la caja es una sola: un día tiene una sola fila). */
    Optional<HorarioCaja> findByDiaSemana(DayOfWeek diaSemana);

    List<HorarioCaja> findByEncargadoIdOrderByDiaSemanaAsc(Long idEncargado);

    List<HorarioCaja> findAllByOrderByDiaSemanaAsc();
}
