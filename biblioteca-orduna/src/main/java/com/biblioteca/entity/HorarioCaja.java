package com.biblioteca.entity;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horario de la caja: un día de la semana, una hora de inicio, una hora de fin y
 * el encargado (un bibliotecario o gerente) que la atiende ese día.
 *
 * Un solo horario por día: la columna "dia_semana" es UNIQUE. Así se puede guardar,
 * por ejemplo, Lunes a Viernes de 08:00 a 20:00 y Sábado de 08:00 a 15:00 sin que
 * dos registros se pisen. Los domingos sin fila simplemente no tienen caja programada.
 *
 * La caja es una sola (ver Caja): este horario no crea cajas, solo dice cuándo se
 * espera que esté abierta y a qué hora debe cerrarse sola.
 */
@Entity
@Table(name = "horarios_caja")
public class HorarioCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario_caja")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, unique = true, length = 10)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /** Quién atiende la caja ese día (null si todavía no se asignó). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_encargado")
    private Usuario encargado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DayOfWeek getDiaSemana() { return diaSemana; }
    public void setDiaSemana(DayOfWeek diaSemana) { this.diaSemana = diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
    public Usuario getEncargado() { return encargado; }
    public void setEncargado(Usuario encargado) { this.encargado = encargado; }
}
