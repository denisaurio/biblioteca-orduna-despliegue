package com.biblioteca.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * 4FN: un libro tiene varios autores y varios géneros, y ambos hechos son independientes.
 * Por eso se separan en dos tablas puente (libros_autores y libros_generos) en vez de
 * una sola tabla libro-autor-género. Hibernate crea ambas tablas desde los @ManyToMany.
 */
@Entity
@Table(name = "libros")
public class Libro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_libro")
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(length = 120)
    private String editorial;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    @Column(name = "num_paginas")
    private Integer numPaginas;

    /**
     * FISICO, VIRTUAL o HIBRIDO. El default esta en el DDL para que los libros ya
     * existentes (creados antes de esta columna) se queden como FISICO.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_libro", length = 20, columnDefinition = "varchar(20) not null default 'FISICO'")
    private TipoLibro tipoLibro = TipoLibro.FISICO;

    /** Precio de una jornada de alquiler del PDF. Solo para VIRTUAL e HIBRIDO. */
    @Column(name = "precio_alquiler_diario", precision = 10, scale = 2)
    private BigDecimal precioAlquilerDiario;

    /** Precio de compra. Del PDF si el libro es virtual, del ejemplar si es fisico. */
    @Column(name = "precio_compra", precision = 10, scale = 2)
    private BigDecimal precioCompra;

    /** URL publica de la portada en Cloudflare R2. Las imagenes NO se guardan en la base de datos. */
    @Column(name = "url_imagen", length = 500)
    private String urlImagen;

    /**
     * Clave del PDF dentro del bucket privado de R2 (ej: pdfs/9786124.pdf).
     * Nunca se devuelve al navegador: el PDF se entrega con una URL firmada.
     */
    @Column(name = "clave_pdf", length = 300)
    private String clavePdf;

    /** Tabla puente libros_autores (id_libro, id_autor). */
    @ManyToMany
    @JoinTable(name = "libros_autores",
            joinColumns = @JoinColumn(name = "id_libro"),
            inverseJoinColumns = @JoinColumn(name = "id_autor"))
    private Set<Autor> autores = new HashSet<>();

    /** Tabla puente libros_generos (id_libro, id_genero). */
    @ManyToMany
    @JoinTable(name = "libros_generos",
            joinColumns = @JoinColumn(name = "id_libro"),
            inverseJoinColumns = @JoinColumn(name = "id_genero"))
    private Set<Genero> generos = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getEditorial() { return editorial; }
    public void setEditorial(String editorial) { this.editorial = editorial; }
    public Integer getAnioPublicacion() { return anioPublicacion; }
    public void setAnioPublicacion(Integer anioPublicacion) { this.anioPublicacion = anioPublicacion; }
    public Integer getNumPaginas() { return numPaginas; }
    public void setNumPaginas(Integer numPaginas) { this.numPaginas = numPaginas; }
    public TipoLibro getTipoLibro() { return tipoLibro; }
    public void setTipoLibro(TipoLibro tipoLibro) { this.tipoLibro = tipoLibro; }
    public BigDecimal getPrecioAlquilerDiario() { return precioAlquilerDiario; }
    public void setPrecioAlquilerDiario(BigDecimal precioAlquilerDiario) { this.precioAlquilerDiario = precioAlquilerDiario; }
    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }
    public String getUrlImagen() { return urlImagen; }
    public void setUrlImagen(String urlImagen) { this.urlImagen = urlImagen; }
    public String getClavePdf() { return clavePdf; }
    public void setClavePdf(String clavePdf) { this.clavePdf = clavePdf; }
    public Set<Autor> getAutores() { return autores; }
    public void setAutores(Set<Autor> autores) { this.autores = autores; }
    public Set<Genero> getGeneros() { return generos; }
    public void setGeneros(Set<Genero> generos) { this.generos = generos; }
}
