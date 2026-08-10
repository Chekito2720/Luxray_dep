package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lecciones",
       indexes = @Index(name = "idx_lecciones_curso", columnList = "curso_id, orden"))
public class Leccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "curso_id", nullable = false)
    private UUID cursoId;

    @Column(name = "seccion_id", length = 120, nullable = false)
    private String seccionId;

    @Column(name = "seccion_titulo", length = 200, nullable = false)
    private String seccionTitulo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(name = "duracion", nullable = false, length = 20)
    private String duracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tipo tipo;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "preguntas", columnDefinition = "JSONB")
    private String preguntas;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public enum Tipo { VIDEO, QUIZ, LECTURA }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCursoId() { return cursoId; }
    public void setCursoId(UUID cursoId) { this.cursoId = cursoId; }
    public String getSeccionId() { return seccionId; }
    public void setSeccionId(String seccionId) { this.seccionId = seccionId; }
    public String getSeccionTitulo() { return seccionTitulo; }
    public void setSeccionTitulo(String seccionTitulo) { this.seccionTitulo = seccionTitulo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public String getPreguntas() { return preguntas; }
    public void setPreguntas(String preguntas) { this.preguntas = preguntas; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
