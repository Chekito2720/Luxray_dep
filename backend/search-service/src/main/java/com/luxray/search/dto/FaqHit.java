package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FaqHit {

    private String id;
    private String pregunta;
    private String respuesta;
    private String categoria;

    public static FaqHit of(String id, String pregunta, String respuesta, String categoria) {
        FaqHit hit = new FaqHit();
        hit.id = id;
        hit.pregunta = pregunta;
        hit.respuesta = respuesta;
        hit.categoria = categoria;
        return hit;
    }
}