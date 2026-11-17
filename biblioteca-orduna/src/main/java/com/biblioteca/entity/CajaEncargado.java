package com.biblioteca.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Quién fue encargado de una caja y desde/hasta qué hora - tabla caja_encargados.
 *
 * Al abrir la caja se crea la primera fila. Cada cambio de encargado cierra la fila
 * actual (hasta = ahora) y abre otra. La fila sin "hasta" es el encargado de ahora.
 * Al cerrar la caja se cierra la última fila.
 */
@Entity
@Table(name = "caja_encargados")
public class CajaEncargado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caja_encargado")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja")
    private Caja caja;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime desde;

    /** null mientras sigue siendo el encargado. */
    private LocalDateTime hasta;

    /** El gerente que lo asignó. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asignado_por")
    private Usuario asignadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Caja getCaja() { return caja; }
    public void setCaja(Caja caja) { this.caja = caja; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getDesde() { return desde; }
    public void setDesde(LocalDateTime desde) { this.desde = desde; }
    public LocalDateTime getHasta() { return hasta; }
    public void setHasta(LocalDateTime hasta) { this.hasta = hasta; }
    public Usuario getAsignadoPor() { return asignadoPor; }
    public void setAsignadoPor(Usuario asignadoPor) { this.asignadoPor = asignadoPor; }
}
