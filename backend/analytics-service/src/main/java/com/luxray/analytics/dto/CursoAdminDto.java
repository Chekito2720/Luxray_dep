package com.luxray.analytics.dto;

import java.util.UUID;

public record CursoAdminDto(
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
        String creadoEn
) {}
