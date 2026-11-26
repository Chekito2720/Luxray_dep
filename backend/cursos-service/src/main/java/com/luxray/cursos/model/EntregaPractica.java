package com.luxray.cursos.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entrega versionada de una práctica. Agrupa los archivos que un alumno
 * adjuntó para una actividad y conserva la evaluación docente de esa versión.
 */
@Entity
@Table(name = "entregas_practica",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_entrega_version",
               columnNames = {"usuario_id", "leccion_id", "version"}),
       indexes = {
           @Index(name = "idx_entregas_usuario_leccion", columnList = "usuario_id,leccion_id,version"),
           @Index(name = "idx_entregas_curso_estado", columnList = "curso_id,estado,creado_en")
       })
public class EntregaPractica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "leccion_id", nullable = false)
    private UUID leccionId;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(nullable = false)
    private Integer version = 1;

    /** INCOMPLETA, PENDIENTE, APROBADA o RECHAZADA. */
    @Column(nullable = false, length = 20)
    private String estado = "INCOMPLETA";

    @Column(name = "retroalimentacion", columnDefinition = "TEXT")
    private String retroalimentacion;

    @Column(name = "revisada_por")
    private UUID revisadaPor;

    @Column(name = "revisada_en")
    private LocalDateTime revisadaEn;

    /** Instantánea JSON de los criterios y puntos evaluados por el profesor. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criterios_evaluacion", columnDefinition = "JSONB")
    private JsonNode criteriosEvaluacion;

    /** Calificación final expresada de 0 a 100. */
    @Column
    private Integer puntuacion;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getLeccionId() { return leccionId; }
    public void setLeccionId(UUID leccionId) { this.leccionId = leccionId; }
    public UUID getCursoId() { return cursoId; }
    public void setCursoId(UUID cursoId) { this.cursoId = cursoId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getRetroalimentacion() { return retroalimentacion; }
    public void setRetroalimentacion(String retroalimentacion) { this.retroalimentacion = retroalimentacion; }
    public UUID getRevisadaPor() { return revisadaPor; }
    public void setRevisadaPor(UUID revisadaPor) { this.revisadaPor = revisadaPor; }
    public LocalDateTime getRevisadaEn() { return revisadaEn; }
    public void setRevisadaEn(LocalDateTime revisadaEn) { this.revisadaEn = revisadaEn; }
    public JsonNode getCriteriosEvaluacion() { return criteriosEvaluacion; }
    public void setCriteriosEvaluacion(JsonNode criteriosEvaluacion) { this.criteriosEvaluacion = criteriosEvaluacion; }
    public Integer getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Integer puntuacion) { this.puntuacion = puntuacion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
