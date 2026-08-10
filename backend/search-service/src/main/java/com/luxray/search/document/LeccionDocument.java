package com.luxray.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "lecciones")
@Setting(settingPath = "/elasticsearch/lecciones-settings.json")
public class LeccionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String cursoId;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String titulo;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String descripcion;

    @Field(type = FieldType.Keyword)
    private String seccionTitulo;

    @Field(type = FieldType.Keyword)
    private String tipo;

    @Field(type = FieldType.Keyword)
    private String duracion;

    @Field(type = FieldType.Keyword)
    private List<String> suggest;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCursoId() { return cursoId; }
    public void setCursoId(String cursoId) { this.cursoId = cursoId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getSeccionTitulo() { return seccionTitulo; }
    public void setSeccionTitulo(String seccionTitulo) { this.seccionTitulo = seccionTitulo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public List<String> getSuggest() { return suggest; }
    public void setSuggest(List<String> suggest) { this.suggest = suggest; }
}