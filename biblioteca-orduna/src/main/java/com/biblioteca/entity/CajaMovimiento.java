package com.biblioteca.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimiento de dinero de la caja - tabla caja_movimientos.
 *
 * El movimiento es la ÚNICA fuente de verdad del dinero: el total del día es la suma
 * de los movimientos del día, así que no hay dos sitios donde se pueda desajustar.
 *
 * Las columnas id_prestamo y id_pago_multa tienen índice UNIQUE: el mismo préstamo
 * (o el mismo pago de multa) no se puede cobrar dos veces. Son NULL cuando el
 * movimiento no viene de un préstamo o de una multa, y en MySQL un UNIQUE admite
 * varios NULL, así que no estorban.
 */
@Entity
@Table(name = "caja_movimientos")
public class CajaMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long id;

    /** La caja a la que entró el dinero. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja")
    private Caja caja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimientoCaja tipo;

    /** Dinero que entró a la caja. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /**
     * Parte de la deuda que se condonó (el 15% de las multas pagadas en efectivo).
     * No es dinero que entró: es la diferencia entre lo que se saldó y lo que se cobró.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal descuento;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    /** Para qué fue el cobro (texto legible). */
    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Quién lo cobró. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    /** Préstamo que se cobró al devolver (UNIQUE: no se cobra dos veces). */
    @Column(name = "id_prestamo", unique = true)
    private Long idPrestamo;

    /** Pago de multa que originó el movimiento (UNIQUE). */
    @Column(name = "id_pago_multa", unique = true)
    private Long idPagoMulta;

    /** Devolución en la que se cobró el préstamo. */
    @Column(name = "id_devolucion")
    private Long idDevolucion;

    /** Pedido de libro virtual que se cobró. */
    @Column(name = "id_acceso_virtual")
    private Long idAccesoVirtual;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Caja getCaja() { return caja; }
    public void setCaja(Caja caja) { this.caja = caja; }
    public TipoMovimientoCaja getTipo() { return tipo; }
    public void setTipo(TipoMovimientoCaja tipo) { this.tipo = tipo; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Long getIdPrestamo() { return idPrestamo; }
    public void setIdPrestamo(Long idPrestamo) { this.idPrestamo = idPrestamo; }
    public Long getIdPagoMulta() { return idPagoMulta; }
    public void setIdPagoMulta(Long idPagoMulta) { this.idPagoMulta = idPagoMulta; }
    public Long getIdDevolucion() { return idDevolucion; }
    public void setIdDevolucion(Long idDevolucion) { this.idDevolucion = idDevolucion; }
    public Long getIdAccesoVirtual() { return idAccesoVirtual; }
    public void setIdAccesoVirtual(Long idAccesoVirtual) { this.idAccesoVirtual = idAccesoVirtual; }
}
