package com.biblioteca.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * La caja (el cajón de dinero de la biblioteca) - tabla cajas.
 *
 * Es UNA SOLA para toda la biblioteca: solo puede haber una abierta a la vez y el
 * personal se turna. Para que eso no dependa solo del código, la columna
 * "caja_abierta" tiene índice UNIQUE y vale 1 mientras la caja está abierta y NULL
 * cuando se cierra. En MySQL un índice UNIQUE admite varios NULL, así que solo
 * puede existir una fila con caja_abierta = 1: la base lo garantiza.
 *
 * Solo el GERENTE la abre y la cierra. Al abrirla elige un ENCARGADO (un empleado):
 * solo el encargado (o el gerente) cobra mientras la caja está abierta. El gerente
 * puede cambiar el encargado sin cerrarla; cada cambio queda en caja_encargados.
 * Al final el encargado cuenta el efectivo (arqueo) y el gerente cierra.
 *
 * La caja NO lleva turno_id: la caja es la sesión de dinero y el turno es la
 * bitácora del personal. Son dos cosas distintas que conviven.
 *
 * "monto_inicial" es el dinero que ya había en el cajón al abrirla.
 * "monto_final_declarado" es el efectivo que se contó al cerrarla.
 * "diferencia" es la resta (positivo = sobrante, negativo = faltante) y solo
 * mira el efectivo: lo que se cobró con tarjeta o Yape no está en el cajón.
 */
@Entity
@Table(name = "cajas")
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caja")
    private Long id;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Quién abrió la caja. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_apertura")
    private Usuario usuarioApertura;

    /** Quién la cerró (null mientras está abierta). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cierre")
    private Usuario usuarioCierre;

    /** Dinero que ya había en el cajón al abrir. */
    @Column(name = "monto_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoInicial = BigDecimal.ZERO;

    /** Efectivo contado al cerrar. */
    @Column(name = "monto_final_declarado", precision = 10, scale = 2)
    private BigDecimal montoFinalDeclarado;

    /** Sobrante (positivo) o faltante (negativo) al arquear. */
    @Column(precision = 10, scale = 2)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaja estado = EstadoCaja.ABIERTA;

    /**
     * 1 mientras la caja está abierta, NULL cuando se cierra.
     * UNIQUE: la base no deja abrir dos cajas a la vez.
     */
    @Column(name = "caja_abierta", unique = true)
    private Integer cajaAbierta = 1;

    @Column(length = 300)
    private String observaciones;

    /** Empleado encargado ahora (el único que cobra, junto con el gerente). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_encargado")
    private Usuario encargado;

    /** Efectivo que contó el encargado (arqueo). El gerente lo usa al cerrar. */
    @Column(name = "monto_contado_encargado", precision = 10, scale = 2)
    private BigDecimal montoContadoEncargado;

    @Column(name = "fecha_arqueo")
    private LocalDateTime fechaArqueo;

    /**
     * Copia de la hora de fin del horario vigente cuando se abrió esta caja.
     * Se congela al abrir: si el gerente cambia el horario después, esta caja
     * sigue cerrándose a la hora con la que fue abierta.
     */
    @Column(name = "cierre_programado")
    private LocalDateTime cierreProgramado;

    /** Quién declaró el arqueo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_arqueo")
    private Usuario usuarioArqueo;

    public Usuario getEncargado() { return encargado; }
    public void setEncargado(Usuario encargado) { this.encargado = encargado; }
    public BigDecimal getMontoContadoEncargado() { return montoContadoEncargado; }
    public void setMontoContadoEncargado(BigDecimal montoContadoEncargado) { this.montoContadoEncargado = montoContadoEncargado; }
    public LocalDateTime getFechaArqueo() { return fechaArqueo; }
    public void setFechaArqueo(LocalDateTime fechaArqueo) { this.fechaArqueo = fechaArqueo; }
    public Usuario getUsuarioArqueo() { return usuarioArqueo; }
    public void setUsuarioArqueo(Usuario usuarioArqueo) { this.usuarioArqueo = usuarioArqueo; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public LocalDateTime getCierreProgramado() { return cierreProgramado; }
    public void setCierreProgramado(LocalDateTime cierreProgramado) { this.cierreProgramado = cierreProgramado; }
    public Usuario getUsuarioApertura() { return usuarioApertura; }
    public void setUsuarioApertura(Usuario usuarioApertura) { this.usuarioApertura = usuarioApertura; }
    public Usuario getUsuarioCierre() { return usuarioCierre; }
    public void setUsuarioCierre(Usuario usuarioCierre) { this.usuarioCierre = usuarioCierre; }
    public BigDecimal getMontoInicial() { return montoInicial; }
    public void setMontoInicial(BigDecimal montoInicial) { this.montoInicial = montoInicial; }
    public BigDecimal getMontoFinalDeclarado() { return montoFinalDeclarado; }
    public void setMontoFinalDeclarado(BigDecimal montoFinalDeclarado) { this.montoFinalDeclarado = montoFinalDeclarado; }
    public BigDecimal getDiferencia() { return diferencia; }
    public void setDiferencia(BigDecimal diferencia) { this.diferencia = diferencia; }
    public EstadoCaja getEstado() { return estado; }
    public void setEstado(EstadoCaja estado) { this.estado = estado; }
    public Integer getCajaAbierta() { return cajaAbierta; }
    public void setCajaAbierta(Integer cajaAbierta) { this.cajaAbierta = cajaAbierta; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
