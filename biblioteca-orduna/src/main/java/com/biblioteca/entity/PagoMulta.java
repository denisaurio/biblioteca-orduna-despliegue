package com.biblioteca.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos_multa")
public class PagoMulta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long id;

    /** Una multa puede tener varios pagos (pago parcial). */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_multa")
    private Multa multa;

    @Column(name = "monto_pagado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPagado;

    /**
     * Descuento aplicado por pagar en efectivo (15%). Es la parte de la deuda que
     * se cancela sin que entre dinero a la caja: monto_pagado es el efectivo real
     * recibido y descuento es lo que se condonó.
     */
    @Column(name = "descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bibliotecario")
    private Usuario bibliotecario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Multa getMulta() { return multa; }
    public void setMulta(Multa multa) { this.multa = multa; }
    public BigDecimal getMontoPagado() { return montoPagado; }
    public void setMontoPagado(BigDecimal montoPagado) { this.montoPagado = montoPagado; }
    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }
    public Usuario getBibliotecario() { return bibliotecario; }
    public void setBibliotecario(Usuario bibliotecario) { this.bibliotecario = bibliotecario; }
}
