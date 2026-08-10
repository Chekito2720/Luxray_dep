package com.luxray.cursos.dto;

import java.util.List;

/**
 * Resultado de búsqueda interna. Agrupa los hits por tipo para que el frontend
 * pueda renderizar secciones (cursos / lecciones / conceptos del dominio).
 */
public record BusquedaResponse(
        String query,
        long total,
        List<CursoHit> cursos,
        List<LeccionHit> lecciones,
        List<ConceptoHit> conceptos,
        List<FaqHit> faqs
) {
    public record CursoHit(
            String id,
            String titulo,
            String descripcion,
            String nivel,
            String nivelKey,
            String instructor,
            String snippet
    ) {}

    public record LeccionHit(
            String id,
            String cursoId,
            String cursoTitulo,
            String titulo,
            String descripcion,
            String tipo,
            String snippet
    ) {}

    public record ConceptoHit(
            String termino,
            String definicion,
            List<String> sinonimos,
            List<String> temas,
            String snippet
    ) {}

    public record FaqHit(
            String id,
            String pregunta,
            String respuesta,
            String categoria,
            String snippet
    ) {}
}
