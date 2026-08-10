package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cursos", indexes = {
        @Index(name = "idx_cursos_nivel", columnList = "nivel"),
        @Index(name = "idx_cursos_publicado", columnList = "publicado")
})
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nivel nivel;

    @Column(nullable = false)
    private Integer semanas = 12;

    @Column(nullable = false)
    private Integer lecciones = 0;

    @Column(name = "estudiantes", nullable = false)
    private Integer estudiantes = 0;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false, length = 120)
    private String instructor;

    @Column(nullable = false, length = 16)
    private String icon = "pi-bolt";

    @Column(nullable = false, length = 16)
    private String color = "#1565c0";

    @Column(nullable = false)
    private Boolean publicado = true;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public enum Nivel { BASICO, INTERMEDIO, AVANZADO }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Nivel getNivel() { return nivel; }
    public void setNivel(Nivel nivel) { this.nivel = nivel; }
    public Integer getSemanas() { return semanas; }
    public void setSemanas(Integer semanas) { this.semanas = semanas; }
    public Integer getLecciones() { return lecciones; }
    public void setLecciones(Integer lecciones) { this.lecciones = lecciones; }
    public Integer getEstudiantes() { return estudiantes; }
    public void setEstudiantes(Integer estudiantes) { this.estudiantes = estudiantes; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Boolean getPublicado() { return publicado; }
    public void setPublicado(Boolean publicado) { this.publicado = publicado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
