package com.biblioteca.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos")
public class Prestamo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prestamo")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ejemplar")
    private Ejemplar ejemplar;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario lector;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bibliotecario")
    private Usuario bibliotecario;

    /**
     * FECHA_FIJA (fijo) o PERSONALIZADO. Se guarda como varchar y no como enum de MySQL para
     * poder agregar tipos sin romper la tabla (data.sql convierte la columna antigua).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_prestamo", nullable = false, length = 20, columnDefinition = "varchar(20) not null")
    private TipoPrestamo tipo = TipoPrestamo.FECHA_FIJA;

    /** Días de préstamo: los de la configuración (fijo) o los que eligió el personal (personalizado). */
    @Column(name = "dias_prestamo")
    private Integer diasPrestamo;

    /** true si la tarifa de multa la escribió el personal; false si se copió de la configuración. */
    @Column(name = "tarifa_personalizada")
    private Boolean tarifaPersonalizada;

    @Column(name = "fecha_prestamo", nullable = false)
    private LocalDateTime fechaPrestamo;

    /** Fecha límite de devolución. null solo en préstamos ABIERTOS antiguos. */
    @Column(name = "fecha_devolucion_esperada")
    private LocalDate fechaVencimiento;

    /**
     * Tarifa vigente al momento del préstamo. Así un cambio posterior de la tarifa
     * no afecta de forma retroactiva a este préstamo (regla del documento).
     * Es la de la configuración o una personalizada. null solo en préstamos ABIERTOS antiguos.
     */
    @Column(name = "tarifa_multa_diaria", precision = 10, scale = 2)
    private BigDecimal tarifaMultaDiaria;

    /**
     * Precio del ejemplar en el momento del préstamo. Se COPIA, igual que la tarifa
     * de multa: cambiar después el precio del libro o del ejemplar no altera los
     * préstamos ya hechos (sirve para saber cuánto vale lo que salió de la biblioteca).
     * Vale igual en las dos rutas: fecha fija y abierto.
     */
    @Column(name = "precio_copia", precision = 10, scale = 2)
    private BigDecimal precioCopia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPrestamo estado = EstadoPrestamo.ACTIVO;

    /** true si es de fecha fija, sigue activo y ya pasó su fecha. */
    public boolean estaVencido(LocalDate hoy) {
        return estado == EstadoPrestamo.ACTIVO && fechaVencimiento != null && fechaVencimiento.isBefore(hoy);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Ejemplar getEjemplar() { return ejemplar; }
    public void setEjemplar(Ejemplar ejemplar) { this.ejemplar = ejemplar; }
    public Usuario getLector() { return lector; }
    public void setLector(Usuario lector) { this.lector = lector; }
    public Usuario getBibliotecario() { return bibliotecario; }
    public void setBibliotecario(Usuario bibliotecario) { this.bibliotecario = bibliotecario; }
    public TipoPrestamo getTipo() { return tipo; }
    public void setTipo(TipoPrestamo tipo) { this.tipo = tipo; }
    public Integer getDiasPrestamo() { return diasPrestamo; }
    public void setDiasPrestamo(Integer diasPrestamo) { this.diasPrestamo = diasPrestamo; }
    public boolean isTarifaPersonalizada() { return Boolean.TRUE.equals(tarifaPersonalizada); }
    public void setTarifaPersonalizada(Boolean tarifaPersonalizada) { this.tarifaPersonalizada = tarifaPersonalizada; }
    public LocalDateTime getFechaPrestamo() { return fechaPrestamo; }
    public void setFechaPrestamo(LocalDateTime fechaPrestamo) { this.fechaPrestamo = fechaPrestamo; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public BigDecimal getTarifaMultaDiaria() { return tarifaMultaDiaria; }
    public void setTarifaMultaDiaria(BigDecimal tarifaMultaDiaria) { this.tarifaMultaDiaria = tarifaMultaDiaria; }
    public BigDecimal getPrecioCopia() { return precioCopia; }
    public void setPrecioCopia(BigDecimal precioCopia) { this.precioCopia = precioCopia; }
    public EstadoPrestamo getEstado() { return estado; }
    public void setEstado(EstadoPrestamo estado) { this.estado = estado; }
}
