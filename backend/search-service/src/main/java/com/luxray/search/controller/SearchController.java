package com.luxray.search.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.search.document.ConceptoDocument;
import com.luxray.search.document.CursoDocument;
import com.luxray.search.document.FaqDocument;
import com.luxray.search.document.LeccionDocument;
import com.luxray.search.dto.SearchRequest;
import com.luxray.search.dto.SearchResponse;
import com.luxray.search.repository.ConceptoDocumentRepository;
import com.luxray.search.repository.CursoDocumentRepository;
import com.luxray.search.repository.FaqDocumentRepository;
import com.luxray.search.repository.LeccionDocumentRepository;
import com.luxray.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Búsqueda Unificada", description = "Endpoint unificado para buscar en cursos, lecciones, conceptos y FAQs")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final CursoDocumentRepository cursoRepo;
    private final LeccionDocumentRepository leccionRepo;
    private final ConceptoDocumentRepository conceptoRepo;
    private final FaqDocumentRepository faqRepo;

    @Operation(summary = "Búsqueda unificada", description = "Busca en cursos, lecciones, conceptos y FAQs")
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> buscar(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) List<String> indices
    ) {
        SearchRequest request = new SearchRequest();
        request.setQ(q);
        request.setPage(page);
        request.setSize(size);
        request.setIndices(indices);

        SearchResponse response = searchService.buscar(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Búsqueda unificada (POST)", description = "Busca con body para queries complejas")
    @PostMapping
    public ResponseEntity<ApiResponse<SearchResponse>> buscarPost(@RequestBody SearchRequest request) {
        SearchResponse response = searchService.buscar(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Poblar índices con datos de prueba", description = "Inserta documentos de ejemplo en Elasticsearch")
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex() {
        CursoDocument curso = CursoDocument.builder()
                .id("curso-1")
                .titulo("Fundamentos de Electricidad")
                .descripcion("Curso básico de electricidad, ley de Ohm, circuitos")
                .instructor("Prof. García")
                .nivel("PRINCIPIANTE")
                .icon("zap")
                .color("#FFD700")
                .semanas(4)
                .lecciones(12)
                .publicado(true)
                .tags(List.of("electricidad", "ohm", "circuitos"))
                .suggest(List.of("electricidad", "ohm", "ley de ohm", "circuitos"))
                .build();
        cursoRepo.save(curso);

        LeccionDocument leccion = LeccionDocument.builder()
                .id("leccion-1")
                .cursoId("curso-1")
                .titulo("Ley de Ohm")
                .descripcion("Explicación de V=I*R y aplicaciones prácticas")
                .seccionTitulo("Módulo 1: Conceptos básicos")
                .tipo("VIDEO")
                .duracion("900")
                .build();
        leccionRepo.save(leccion);

        ConceptoDocument concepto = ConceptoDocument.builder()
                .id("concepto-1")
                .termino("Ley de Ohm")
                .definicion("La corriente que circula por un conductor es directamente proporcional al voltaje e inversamente proporcional a la resistencia")
                .sinonimos(List.of("V=IR", "Relación voltaje-corriente-resistencia"))
                .temas(List.of("electricidad", "física", "circuitos"))
                .build();
        conceptoRepo.save(concepto);

        // Nuevos conceptos enriquecidos
        ConceptoDocument colores = ConceptoDocument.builder()
                .id("concepto-cables-color")
                .termino("Código de colores en cables eléctricos")
                .definicion("En instalaciones residenciales: verde/amarillo = tierra/protección; azul = neutro; negro/rojo/café = fase o línea activa (127/220 V). En baja tensión: rojo (+), negro (-), verde/amarillo (tierra). Siempre verificar con multímetro antes de tocar.")
                .sinonimos(List.of("colores cables", "norma NOM-001-SEDE", "tierra neutro fase"))
                .temas(List.of("electricidad", "instalación", "seguridad"))
                .suggest(List.of("cables", "colores", "tierra", "neutro", "fase"))
                .build();
        conceptoRepo.save(colores);

        ConceptoDocument herramientas = ConceptoDocument.builder()
                .id("concepto-herramientas")
                .termino("Herramientas de electricidad básica")
                .definicion("Multímetro (medición V/A/Ω), alicate de corte/pelacables, destornillador aislado (1000 V), cinta aislante, pinza amperimétrica y probador de tensión. Siempre usar equipo con aislamiento certificado para evitar descargas.")
                .sinonimos(List.of("multímetro", "alicate", "destornillador aislado", "pelacables"))
                .temas(List.of("electricidad", "herramientas", "seguridad"))
                .suggest(List.of("multímetro", "alicate", "destornillador", "pelacables", "cinta aislante"))
                .build();
        conceptoRepo.save(herramientas);

        ConceptoDocument voltaje = ConceptoDocument.builder()
                .id("concepto-voltaje-127")
                .termino("Voltaje doméstico (127 V)")
                .definicion("En México, el voltaje doméstico monofásico es 127 V (fase-neutro) o 220 V (fase-fase). Es el nivel estándar para tomas de corriente, iluminación y pequeños electrodomésticos en hogares. No confundir con baja tensión (12 V, 24 V) usada en electrónica o control.")
                .sinonimos(List.of("127 V", "voltaje residencial", "toma de corriente", "fase neutro"))
                .temas(List.of("electricidad", "voltaje", "instalación residencial"))
                .suggest(List.of("127", "voltaje", "residencial", "fase", "neutro"))
                .build();
        conceptoRepo.save(voltaje);

        ConceptoDocument corriente = ConceptoDocument.builder()
                .id("concepto-corriente-alterna")
                .termino("Corriente alterna (CA / AC)")
                .definicion("Tipo de corriente eléctrica en la que el flujo de electrones cambia de dirección periódicamente (60 Hz en México). Es la forma de energía que llega a los hogares por la red eléctrica. Se diferencia de la corriente continua (CC / DC) que fluye en una sola dirección, como en baterías y paneles solares.")
                .sinonimos(List.of("AC", "corriente alterna", "60 Hz", "red eléctrica"))
                .temas(List.of("electricidad", "corriente", "instalación"))
                .suggest(List.of("AC", "corriente alterna", "60 Hz", "red"))
                .build();
        conceptoRepo.save(corriente);

        ConceptoDocument fusible = ConceptoDocument.builder()
                .id("concepto-fusible")
                .termino("Fusible y protección de circuitos")
                .definicion("Dispositivo de seguridad que interrumpe el circuito cuando la corriente excede un límite. Evita sobrecalentamiento y riesgos de incendio. En instalaciones modernas se usan interruptores termomagnéticos; los fusibles tradicionales se encuentran en tableros antiguos o automotrices.")
                .sinonimos(List.of("fusible", "protección", "interruptor termomagnético", "sobrecorriente"))
                .temas(List.of("electricidad", "seguridad", "instalación"))
                .suggest(List.of("fusible", "protección", "sobrecorriente", "interruptor"))
                .build();
        conceptoRepo.save(fusible);

        FaqDocument faq = FaqDocument.builder()
                .id("faq-1")
                .pregunta("¿Qué es la ley de Ohm?")
                .respuesta("Es la relación fundamental entre voltaje (V), corriente (I) y resistencia (R): V = I × R")
                .categoria("electricidad")
                .build();
        faqRepo.save(faq);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Datos de prueba insertados");
        result.put("cursos", 1);
        result.put("lecciones", 1);
        result.put("conceptos", 6);
        result.put("faqs", 1);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}