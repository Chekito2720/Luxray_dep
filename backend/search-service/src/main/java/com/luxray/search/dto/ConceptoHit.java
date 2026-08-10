package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ConceptoHit {

    private String id;
    private String termino;
    private String definicion;
    private List<String> sinonimos;
    private List<String> temas;

    public static ConceptoHit of(String id, String termino, String definicion,
                                 List<String> sinonimos, List<String> temas) {
        ConceptoHit hit = new ConceptoHit();
        hit.id = id;
        hit.termino = termino;
        hit.definicion = definicion;
        hit.sinonimos = sinonimos;
        hit.temas = temas;
        return hit;
    }
}