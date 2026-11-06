package com.biblioteca.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Orden de acceso a un libro virtual (tabla accesos_virtuales).
 *
 * Es la fila que une a un lector con un libro virtual y con un pago:
 *   - el lector pide el libro (alquiler por dias o compra);
 *   - paga con tarjeta, Yape o en efectivo en la tienda;
 *   - si paga en tienda, el personal busca la orden por su codigo de 24 horas.
 *
 * El precio se COPIA aqui (igual que la tarifa de multa en el prestamo): si el
 * librarian cambia el precio del libro, las ordenes ya hechas no cambian.
 *
 * Lo que NO se guarda es si el alquiler ya vencio: sale de fecha_fin.
 */
@Entity
@Table(name = "accesos_virtuales")
public class AccesoVirtual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_acceso_virtual")
    private Long id;

    /** El lector que pidio el libro. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_libro")
    private Libro libro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAccesoVirtual tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAccesoVirtual estado = EstadoAccesoVirtual.PENDIENTE_PAGO;

    /** Precio copiado del libro al momento de pedirlo. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    /** Dias de alquiler. null en una compra. */
    @Column(name = "dias_alquiler")
    private Integer diasAlquiler;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    /** Empieza el acceso cuando se cobra. null mientras esta pendiente de pago. */
    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    /** Termina el acceso. En una compra es null (el acceso es permanente). */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** Numero de cola que ve el cliente cuando paga en la tienda. */
    @Column(name = "numero_cola", length = 12)
    private String numeroCola;

    /** Codigo que el cliente muestra en el mostrador. Vale 24 horas. */
    @Column(name = "codigo_retiro", unique = true, length = 12)
    private String codigoRetiro;

    @Column(name = "fecha_codigo_expira")
    private LocalDateTime fechaCodigoExpira;

    /** Referencia del pago externo: codigo de operacion de Yape, id de la orden, etc. */
    @Column(name = "referencia_pago", length = 80)
    private String referenciaPago;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** El alquiler ya termino: se calcula con la fecha, no se guarda un estado "vencido". */
    public boolean vencido(LocalDate hoy) {
        return fechaFin != null && fechaFin.isBefore(hoy);
    }

    /** Dias que le quedan de alquiler. 0 si ya vencio o si es una compra. */
    public long diasRestantes(LocalDate hoy) {
        if (tipo != TipoAccesoVirtual.ALQUILER || fechaFin == null) return 0L;
        return Math.max(0L, ChronoUnit.DAYS.between(hoy, fechaFin));
    }

    /** El codigo de retiro ya no sirve (pasaron las 24 horas). */
    public boolean codigoExpirado(LocalDateTime ahora) {
        return fechaCodigoExpira == null || fechaCodigoExpira.isBefore(ahora);
    }

    /** El lector ya tiene el libro en la mano (pago confirmado). */
    public boolean pagado() {
        return estado == EstadoAccesoVirtual.PAGADO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Libro getLibro() { return libro; }
    public void setLibro(Libro libro) { this.libro = libro; }
    public TipoAccesoVirtual getTipo() { return tipo; }
    public void setTipo(TipoAccesoVirtual tipo) { this.tipo = tipo; }
    public EstadoAccesoVirtual getEstado() { return estado; }
    public void setEstado(EstadoAccesoVirtual estado) { this.estado = estado; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getDiasAlquiler() { return diasAlquiler; }
    public void setDiasAlquiler(Integer diasAlquiler) { this.diasAlquiler = diasAlquiler; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public String getNumeroCola() { return numeroCola; }
    public void setNumeroCola(String numeroCola) { this.numeroCola = numeroCola; }
    public String getCodigoRetiro() { return codigoRetiro; }
    public void setCodigoRetiro(String codigoRetiro) { this.codigoRetiro = codigoRetiro; }
    public LocalDateTime getFechaCodigoExpira() { return fechaCodigoExpira; }
    public void setFechaCodigoExpira(LocalDateTime fechaCodigoExpira) { this.fechaCodigoExpira = fechaCodigoExpira; }
    public String getReferenciaPago() { return referenciaPago; }
    public void setReferenciaPago(String referenciaPago) { this.referenciaPago = referenciaPago; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
