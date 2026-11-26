package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mensaje persistente de un canal de foro por curso. */
@Entity
@Table(name = "mensajes_foro",
       indexes = @Index(name = "idx_foro_curso_creado", columnList = "curso_id,creado_en"))
public class MensajeForo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "autor_nombre", nullable = false, length = 160)
    private String autorNombre;

    @Column(name = "autor_rol", nullable = false, length = 20)
    private String autorRol;

    @Column(nullable = false, length = 2000)
    private String contenido;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCursoId() { return cursoId; }
    public void setCursoId(UUID cursoId) { this.cursoId = cursoId; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getAutorNombre() { return autorNombre; }
    public void setAutorNombre(String autorNombre) { this.autorNombre = autorNombre; }
    public String getAutorRol() { return autorRol; }
    public void setAutorRol(String autorRol) { this.autorRol = autorRol; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
