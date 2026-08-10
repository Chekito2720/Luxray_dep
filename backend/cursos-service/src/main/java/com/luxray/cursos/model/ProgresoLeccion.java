package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "progreso_lecciones",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "leccion_id"}),
       indexes = @Index(name = "idx_prog_usuario", columnList = "usuario_id"))
public class ProgresoLeccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "leccion_id", nullable = false)
    private UUID leccionId;

    @Column(name = "completada", nullable = false)
    private Boolean completada = false;

    @Column(name = "puntuacion")
    private Integer puntuacion;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getLeccionId() { return leccionId; }
    public void setLeccionId(UUID leccionId) { this.leccionId = leccionId; }
    public Boolean getCompletada() { return completada; }
    public void setCompletada(Boolean completada) { this.completada = completada; }
    public Integer getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Integer puntuacion) { this.puntuacion = puntuacion; }
    public LocalDateTime getCompletadoEn() { return completadoEn; }
    public void setCompletadoEn(LocalDateTime completadoEn) { this.completadoEn = completadoEn; }
}
