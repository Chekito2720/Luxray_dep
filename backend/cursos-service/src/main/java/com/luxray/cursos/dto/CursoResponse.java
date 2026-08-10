package com.luxray.cursos.dto;

import com.luxray.cursos.model.Curso;

import java.time.LocalDateTime;
import java.util.UUID;

public record CursoResponse(
        UUID id,
        String titulo,
        String descripcion,
        String nivel,
        String nivelKey,
        Integer semanas,
        Integer lecciones,
        Integer estudiantes,
        Double rating,
        String instructor,
        String icon,
        String color,
        Boolean publicado,
        LocalDateTime creadoEn
) {
    public static CursoResponse from(Curso c) {
        return new CursoResponse(
                c.getId(),
                c.getTitulo(),
                c.getDescripcion(),
                c.getNivel().name().charAt(0) + c.getNivel().name().substring(1).toLowerCase(),
                c.getNivel().name().toLowerCase(),
                c.getSemanas(),
                c.getLecciones(),
                c.getEstudiantes(),
                c.getRating(),
                c.getInstructor(),
                c.getIcon(),
                c.getColor(),
                c.getPublicado(),
                c.getCreadoEn()
        );
    }
}
