package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Criterio de evaluación de una actividad mediante lista de cotejo. */
@Entity
@Table(name = "lista_cotejo_items",
       indexes = @Index(name = "idx_cotejo_leccion_orden", columnList = "leccion_id,orden"))
public class ListaCotejoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "leccion_id", nullable = false)
    private UUID leccionId;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false)
    private Integer puntos;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getLeccionId() { return leccionId; }
    public void setLeccionId(UUID leccionId) { this.leccionId = leccionId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getPuntos() { return puntos; }
    public void setPuntos(Integer puntos) { this.puntos = puntos; }
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
