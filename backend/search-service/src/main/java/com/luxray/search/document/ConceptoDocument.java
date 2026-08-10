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
@Document(indexName = "conceptos")
@Setting(settingPath = "/elasticsearch/conceptos-settings.json")
public class ConceptoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String termino;

    @Field(type = FieldType.Text, analyzer = "spanish")
    private String definicion;

    @Field(type = FieldType.Keyword)
    private List<String> sinonimos;

    @Field(type = FieldType.Keyword)
    private List<String> temas;

    @Field(type = FieldType.Keyword)
    private List<String> suggest;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTermino() { return termino; }
    public void setTermino(String termino) { this.termino = termino; }
    public String getDefinicion() { return definicion; }
    public void setDefinicion(String definicion) { this.definicion = definicion; }
    public List<String> getSinonimos() { return sinonimos; }
    public void setSinonimos(List<String> sinonimos) { this.sinonimos = sinonimos; }
    public List<String> getTemas() { return temas; }
    public void setTemas(List<String> temas) { this.temas = temas; }
    public List<String> getSuggest() { return suggest; }
    public void setSuggest(List<String> suggest) { this.suggest = suggest; }
}