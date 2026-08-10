package com.luxray.analytics.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Snapshot diario de métricas por usuario — alimenta el dashboard.
 * Para v1 se rellena por eventos; en v2 un job lo precalcula.
 */
@Entity
@Table(name = "analytics_snapshots",
       indexes = @Index(name = "idx_snap_usuario_fecha", columnList = "usuario_id, fecha"))
public class AnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "cursos_inscritos", nullable = false)
    private Integer cursosInscritos = 0;

    @Column(name = "lecciones_completadas", nullable = false)
    private Integer leccionesCompletadas = 0;

    @Column(name = "horas_estudio", nullable = false)
    private Double horasEstudio = 0.0;

    @Column(name = "promedio_quizzes", nullable = false)
    private Double promedioQuizzes = 0.0;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public Integer getCursosInscritos() { return cursosInscritos; }
    public void setCursosInscritos(Integer cursosInscritos) { this.cursosInscritos = cursosInscritos; }
    public Integer getLeccionesCompletadas() { return leccionesCompletadas; }
    public void setLeccionesCompletadas(Integer leccionesCompletadas) { this.leccionesCompletadas = leccionesCompletadas; }
    public Double getHorasEstudio() { return horasEstudio; }
    public void setHorasEstudio(Double horasEstudio) { this.horasEstudio = horasEstudio; }
    public Double getPromedioQuizzes() { return promedioQuizzes; }
    public void setPromedioQuizzes(Double promedioQuizzes) { this.promedioQuizzes = promedioQuizzes; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
