package com.luxray.cursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Indicador de una sola fila que registra si ya se configuraron asignaciones. */
@Entity
@Table(name = "asignaciones_estado")
public class EstadoAsignaciones {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "inicializado", nullable = false)
    private Boolean inicializado = false;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Boolean getInicializado() { return inicializado; }
    public void setInicializado(Boolean inicializado) { this.inicializado = inicializado; }
}
