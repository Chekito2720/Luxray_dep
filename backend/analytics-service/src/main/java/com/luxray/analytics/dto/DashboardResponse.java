package com.luxray.analytics.dto;

import java.util.List;

public record DashboardResponse(
        List<KpiDto> kpis,
        InscripcionesDto inscripciones6m,
        ProgresoDto progresoNiveles,
        TiempoModuloDto tiempoPorModulo,
        DistNivelesDto distribucionNiveles,
        List<TopCursoDto> topCursos
) {
    public record InscripcionesDto(List<String> labels, List<Number> data) {}
    public record ProgresoDto(List<String> labels, List<Number> data) {}
    public record TiempoModuloDto(List<String> labels, List<Number> data) {}
    public record DistNivelesDto(List<String> labels, List<Number> data) {}
}
