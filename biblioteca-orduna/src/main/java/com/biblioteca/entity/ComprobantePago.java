package com.biblioteca.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comprobante de un pago con Yape (tabla comprobantes_pago).
 *
 * Yape no tiene API publica con QR dinamico, asi que el pago se hace a mano:
 * el lector ve el QR de Yape Negocio, paga en su app, saca una captura y la sube
 * aqui. El personal la ve (con una URL firmada) y la ACEPTA o la DENIEGA.
 *
 * El comprobante NO cambia el estado del pedido: el pedido sigue en PENDIENTE_PAGO
 * hasta que alguien acepta. Asi el dedupe de pedidos y el panel del mostrador no se
 * rompen. "En revision" es un estado DERIVADO de aqui.
 *
 * La imagen vive en el bucket privado de R2: en la base solo queda la clave.
 */
@Entity
@Table(name = "comprobantes_pago")
public class ComprobantePago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Long id;

    /** El pedido de libro virtual al que pertenece este comprobante. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_acceso_virtual")
    private AccesoVirtual acceso;

    /** El lector que subio la captura. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario lector;

    /** Monto que dice el pedido: se copia para que el pago sea el que se pidio. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /**
     * Codigo de operacion de Yape que teclea el cliente (opcional).
     * Sirve para que el personal pueda cotejar el pago con su app de Yape.
     */
    @Column(name = "referencia_yape", length = 40)
    private String referenciaYape;

    /** Clave del objeto en el bucket privado de R2. Nunca la URL. */
    @Column(name = "clave_imagen", length = 255)
    private String claveImagen;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoComprobante estado = EstadoComprobante.PENDIENTE;

    /** El personal que reviso el comprobante. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisado_por")
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    /** Por que se denego (lo ve el lector). */
    @Column(length = 300)
    private String motivo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AccesoVirtual getAcceso() { return acceso; }
    public void setAcceso(AccesoVirtual acceso) { this.acceso = acceso; }
    public Usuario getLector() { return lector; }
    public void setLector(Usuario lector) { this.lector = lector; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getReferenciaYape() { return referenciaYape; }
    public void setReferenciaYape(String referenciaYape) { this.referenciaYape = referenciaYape; }
    public String getClaveImagen() { return claveImagen; }
    public void setClaveImagen(String claveImagen) { this.claveImagen = claveImagen; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }
    public EstadoComprobante getEstado() { return estado; }
    public void setEstado(EstadoComprobante estado) { this.estado = estado; }
    public Usuario getRevisadoPor() { return revisadoPor; }
    public void setRevisadoPor(Usuario revisadoPor) { this.revisadoPor = revisadoPor; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(LocalDateTime fechaRevision) { this.fechaRevision = fechaRevision; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
