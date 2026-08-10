package com.luxray.cursos.dto;

import com.luxray.cursos.model.Inscripcion;

import java.util.UUID;

public record InscripcionResponse(
        UUID id,
        UUID cursoId,
        String cursoTitulo,
        Integer leccionesCompletadas,
        Integer totalLecciones,
        String proximaLeccion,
        Integer porcentaje
) {
    public static InscripcionResponse from(Inscripcion i, String tituloCurso, int totalLecciones) {
        return new InscripcionResponse(
                i.getId(),
                i.getCursoId(),
                tituloCurso,
                i.getLeccionesCompletadas(),
                totalLecciones,
                i.getProximaLeccion(),
                i.getPorcentaje()
        );
    }
}
