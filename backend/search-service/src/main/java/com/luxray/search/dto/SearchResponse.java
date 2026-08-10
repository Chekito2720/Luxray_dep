package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SearchResponse {

    private String query;
    private long total;
    private int page;
    private int size;
    private List<SearchHitDTO<CursoHit>> cursos;
    private List<SearchHitDTO<LeccionHit>> lecciones;
    private List<SearchHitDTO<ConceptoHit>> conceptos;
    private List<SearchHitDTO<FaqHit>> faqs;

    public static SearchResponse of(String query, long total, int page, int size,
                                    List<SearchHitDTO<CursoHit>> cursos,
                                    List<SearchHitDTO<LeccionHit>> lecciones,
                                    List<SearchHitDTO<ConceptoHit>> conceptos,
                                    List<SearchHitDTO<FaqHit>> faqs) {
        SearchResponse response = new SearchResponse();
        response.query = query;
        response.total = total;
        response.page = page;
        response.size = size;
        response.cursos = cursos;
        response.lecciones = lecciones;
        response.conceptos = conceptos;
        response.faqs = faqs;
        return response;
    }
}