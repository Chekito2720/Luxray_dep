package com.luxray.cursos.dto;

import com.luxray.cursos.model.Curso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CursoRequest(
        @NotBlank @Size(max = 160) String titulo,
        @NotBlank @Size(max = 500) String descripcion,
        @NotNull Curso.Nivel nivel,
        Integer semanas,
        Integer lecciones,
        String instructor,
        String icon,
        String color,
        Boolean publicado
) {
    public Curso toEntity() {
        Curso c = new Curso();
        c.setTitulo(titulo);
        c.setDescripcion(descripcion);
        c.setNivel(nivel);
        c.setSemanas(semanas != null ? semanas : 12);
        c.setLecciones(lecciones != null ? lecciones : 0);
        c.setInstructor(instructor != null ? instructor : "Por asignar");
        c.setIcon(icon != null ? icon : "pi-bolt");
        c.setColor(color != null ? color : "#1565c0");
        c.setPublicado(publicado != null ? publicado : true);
        return c;
    }

    public void applyTo(Curso c) {
        c.setTitulo(titulo);
        c.setDescripcion(descripcion);
        c.setNivel(nivel);
        if (semanas != null) c.setSemanas(semanas);
        if (lecciones != null) c.setLecciones(lecciones);
        if (instructor != null) c.setInstructor(instructor);
        if (icon != null) c.setIcon(icon);
        if (color != null) c.setColor(color);
        if (publicado != null) c.setPublicado(publicado);
    }
}
