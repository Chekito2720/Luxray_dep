package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inscripciones",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "curso_id"}),
       indexes = {
           @Index(name = "idx_insc_usuario", columnList = "usuario_id"),
           @Index(name = "idx_insc_curso",   columnList = "curso_id")
       })
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "lecciones_completadas")
    private Integer leccionesCompletadas = 0;

    @Column(name = "proxima_leccion", length = 200)
    private String proximaLeccion;

    @Column(name = "porcentaje")
    private Integer porcentaje = 0;

    @Column(name = "inscrito_en", nullable = false)
    private LocalDateTime inscritoEn = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getCursoId() { return cursoId; }
    public void setCursoId(UUID cursoId) { this.cursoId = cursoId; }
    public Integer getLeccionesCompletadas() { return leccionesCompletadas; }
    public void setLeccionesCompletadas(Integer leccionesCompletadas) { this.leccionesCompletadas = leccionesCompletadas; }
    public String getProximaLeccion() { return proximaLeccion; }
    public void setProximaLeccion(String proximaLeccion) { this.proximaLeccion = proximaLeccion; }
    public Integer getPorcentaje() { return porcentaje; }
    public void setPorcentaje(Integer porcentaje) { this.porcentaje = porcentaje; }
    public LocalDateTime getInscritoEn() { return inscritoEn; }
    public void setInscritoEn(LocalDateTime inscritoEn) { this.inscritoEn = inscritoEn; }
}
