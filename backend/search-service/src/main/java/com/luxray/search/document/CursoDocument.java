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
@Document(indexName = "cursos")
@Setting(settingPath = "/elasticsearch/cursos-settings.json")
public class CursoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String titulo;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String descripcion;

    @Field(type = FieldType.Keyword)
    private String instructor;

    @Field(type = FieldType.Keyword)
    private String nivel;

    @Field(type = FieldType.Keyword)
    private String icon;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Integer)
    private Integer semanas;

    @Field(type = FieldType.Integer)
    private Integer lecciones;

    @Field(type = FieldType.Boolean)
    private Boolean publicado;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private List<String> suggest;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getSemanas() { return semanas; }
    public void setSemanas(Integer semanas) { this.semanas = semanas; }
    public Integer getLecciones() { return lecciones; }
    public void setLecciones(Integer lecciones) { this.lecciones = lecciones; }
    public Boolean getPublicado() { return publicado; }
    public void setPublicado(Boolean publicado) { this.publicado = publicado; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<String> getSuggest() { return suggest; }
    public void setSuggest(List<String> suggest) { this.suggest = suggest; }
}