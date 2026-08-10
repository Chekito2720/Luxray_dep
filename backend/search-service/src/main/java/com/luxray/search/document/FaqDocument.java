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
@Document(indexName = "faqs")
@Setting(settingPath = "/elasticsearch/faqs-settings.json")
public class FaqDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String pregunta;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String respuesta;

    @Field(type = FieldType.Keyword)
    private String categoria;

    @Field(type = FieldType.Keyword)
    private List<String> suggest;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPregunta() { return pregunta; }
    public void setPregunta(String pregunta) { this.pregunta = pregunta; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public List<String> getSuggest() { return suggest; }
    public void setSuggest(List<String> suggest) { this.suggest = suggest; }
}