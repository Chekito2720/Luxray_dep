package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CursoHit {

    private String id;
    private String titulo;
    private String descripcion;
    private String instructor;
    private String nivel;
    private String icon;
    private String color;
    private Integer semanas;
    private Integer lecciones;
    private Boolean publicado;

    public static CursoHit of(String id, String titulo, String descripcion, String instructor,
                              String nivel, String icon, String color, Integer semanas,
                              Integer lecciones, Boolean publicado) {
        CursoHit hit = new CursoHit();
        hit.id = id;
        hit.titulo = titulo;
        hit.descripcion = descripcion;
        hit.instructor = instructor;
        hit.nivel = nivel;
        hit.icon = icon;
        hit.color = color;
        hit.semanas = semanas;
        hit.lecciones = lecciones;
        hit.publicado = publicado;
        return hit;
    }
}