package com.biblioteca.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ejemplares")
public class Ejemplar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ejemplar")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_libro")
    private Libro libro;

    @Column(name = "codigo_inventario", nullable = false, unique = true, length = 50)
    private String codigoInventario;

    @Column(length = 100)
    private String ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEjemplar estado = EstadoEjemplar.DISPONIBLE;

    /**
     * Precio de ESTA copia. Puede ser distinto del precio general del libro
     * (una edicion antigua, un ejemplar de un tiraje raro). Si es null, se usa
     * el precio de compra del libro.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal precio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Libro getLibro() { return libro; }
    public void setLibro(Libro libro) { this.libro = libro; }
    public String getCodigoInventario() { return codigoInventario; }
    public void setCodigoInventario(String codigoInventario) { this.codigoInventario = codigoInventario; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public EstadoEjemplar getEstado() { return estado; }
    public void setEstado(EstadoEjemplar estado) { this.estado = estado; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
}
