package com.luxray.analytics.dto;

import java.util.UUID;

public record CursoInscripcionDto(
        UUID id,
        UUID cursoId,
        String cursoTitulo,
        Integer leccionesCompletadas,
        Integer totalLecciones,
        String proximaLeccion,
        Integer porcentaje
) {}
