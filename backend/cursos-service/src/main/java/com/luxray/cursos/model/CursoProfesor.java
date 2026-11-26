package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Asignación de un profesor a una clase/curso. */
@Entity
@Table(name = "curso_profesores",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_curso_profesor",
               columnNames = {"curso_id", "profesor_id"}),
       indexes = @Index(name = "idx_curso_profesores_profesor", columnList = "profesor_id,curso_id"))
public class CursoProfesor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "asignado_en", nullable = false)
    private LocalDateTime asignadoEn = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCursoId() { return cursoId; }
    public void setCursoId(UUID cursoId) { this.cursoId = cursoId; }
    public UUID getProfesorId() { return profesorId; }
    public void setProfesorId(UUID profesorId) { this.profesorId = profesorId; }
    public LocalDateTime getAsignadoEn() { return asignadoEn; }
    public void setAsignadoEn(LocalDateTime asignadoEn) { this.asignadoEn = asignadoEn; }
}
