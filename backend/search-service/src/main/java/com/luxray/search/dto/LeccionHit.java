package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LeccionHit {

    private String id;
    private String cursoId;
    private String titulo;
    private String descripcion;
    private String seccionTitulo;
    private String tipo;
    private String duracion;

    public static LeccionHit of(String id, String cursoId, String titulo, String descripcion,
                                String seccionTitulo, String tipo, String duracion) {
        LeccionHit hit = new LeccionHit();
        hit.id = id;
        hit.cursoId = cursoId;
        hit.titulo = titulo;
        hit.descripcion = descripcion;
        hit.seccionTitulo = seccionTitulo;
        hit.tipo = tipo;
        hit.duracion = duracion;
        return hit;
    }
}