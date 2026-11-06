package com.biblioteca.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Una acción del personal, anotada para que el gerente sepa quién hizo qué - tabla auditorias.
 *
 * La auditoría se escribe EN LA MISMA TRANSACCIÓN que la operación: si la operación se
 * revierte, la auditoría tampoco se guarda. Así no queda rastro de algo que no pasó.
 *
 * "usuario" puede ir en null: hay acciones que hace un lector sin sesión (por ejemplo,
 * el auto-registro). Lo que nunca falta es la acción y la fecha.
 */
@Entity
@Table(name = "auditorias")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long id;

    /** Quién lo hizo (null si fue una acción pública sin sesión). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoAccionAuditoria accion;

    /** Sobre qué se hizo: LIBRO, EJEMPLAR, PRESTAMO, CAJA, TURNO, USUARIO... */
    @Column(length = 40)
    private String entidad;

    /** El id de esa entidad (para poder abrirla desde la auditoría). */
    @Column(name = "entidad_id")
    private Long entidadId;

    /** La frase legible: "Registró el libro 'Cien años de soledad' (ISBN ...)". */
    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoAccionAuditoria getAccion() { return accion; }
    public void setAccion(TipoAccionAuditoria accion) { this.accion = accion; }
    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }
    public Long getEntidadId() { return entidadId; }
    public void setEntidadId(Long entidadId) { this.entidadId = entidadId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
