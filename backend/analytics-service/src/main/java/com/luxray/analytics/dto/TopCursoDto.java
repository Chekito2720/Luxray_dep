package com.luxray.analytics.dto;

public record TopCursoDto(
        String titulo,
        String nivel,
        int inscritos,
        int porcentajeCompletado
) { }
