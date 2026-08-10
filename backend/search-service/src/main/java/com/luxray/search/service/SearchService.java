package com.luxray.search.service;

import com.luxray.search.document.ConceptoDocument;
import com.luxray.search.document.CursoDocument;
import com.luxray.search.document.FaqDocument;
import com.luxray.search.document.LeccionDocument;
import com.luxray.search.dto.ConceptoHit;
import com.luxray.search.dto.CursoHit;
import com.luxray.search.dto.FaqHit;
import com.luxray.search.dto.LeccionHit;
import com.luxray.search.dto.SearchHitDTO;
import com.luxray.search.dto.SearchRequest;
import com.luxray.search.dto.SearchResponse;
import com.luxray.search.repository.ConceptoDocumentRepository;
import com.luxray.search.repository.CursoDocumentRepository;
import com.luxray.search.repository.FaqDocumentRepository;
import com.luxray.search.repository.LeccionDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final CursoDocumentRepository cursoRepo;
    private final LeccionDocumentRepository leccionRepo;
    private final ConceptoDocumentRepository conceptoRepo;
    private final FaqDocumentRepository faqRepo;
    private final ElasticsearchOperations elasticsearchOperations;

    public SearchResponse buscar(SearchRequest request) {
        String q = request.getQ();
        int page = Math.max(0, request.getPage());
        int size = Math.min(20, Math.max(1, request.getSize()));

        log.debug("Buscando: '{}' page={} size={}", q, page, size);

        List<String> indices = request.getIndices() != null && !request.getIndices().isEmpty()
                ? request.getIndices()
                : List.of("cursos", "lecciones", "conceptos", "faqs");

        List<SearchHitDTO<CursoHit>> cursos = buscarCursos(q, page, size, indices);
        List<SearchHitDTO<LeccionHit>> lecciones = buscarLecciones(q, page, size, indices);
        List<SearchHitDTO<ConceptoHit>> conceptos = buscarConceptos(q, page, size, indices);
        List<SearchHitDTO<FaqHit>> faqs = buscarFaqs(q, page, size, indices);

        long total = (long) cursos.size() + lecciones.size() + conceptos.size() + faqs.size();

        return SearchResponse.of(request.getQ(), total, page, size, cursos, lecciones, conceptos, faqs);
    }

    private List<SearchHitDTO<CursoHit>> buscarCursos(String q, int page, int size, List<String> indices) {
        if (!indices.contains("cursos")) return List.of();

        co.elastic.clients.elasticsearch._types.query_dsl.Query multiMatchQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(qb -> qb
                .multiMatch(mm -> mm
                        .query(q)
                        .fields("titulo^3", "descripcion^2", "tags", "instructor", "suggest")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(multiMatchQuery)
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<CursoDocument> hits = elasticsearchOperations.search(nativeQuery, CursoDocument.class);
        return mapHits(hits, CursoHit.class, CursoDocument::getId);
    }

    private List<SearchHitDTO<LeccionHit>> buscarLecciones(String q, int page, int size, List<String> indices) {
        if (!indices.contains("lecciones")) return List.of();

        co.elastic.clients.elasticsearch._types.query_dsl.Query multiMatchQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(qb -> qb
                .multiMatch(mm -> mm
                        .query(q)
                        .fields("titulo^3", "descripcion^2", "seccionTitulo", "tipo")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(multiMatchQuery)
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<LeccionDocument> hits = elasticsearchOperations.search(nativeQuery, LeccionDocument.class);
        return mapHits(hits, LeccionHit.class, LeccionDocument::getId);
    }

    private List<SearchHitDTO<ConceptoHit>> buscarConceptos(String q, int page, int size, List<String> indices) {
        if (!indices.contains("conceptos")) return List.of();

        co.elastic.clients.elasticsearch._types.query_dsl.Query multiMatchQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(qb -> qb
                .multiMatch(mm -> mm
                        .query(q)
                        .fields("termino^3", "definicion^2", "sinonimos", "temas")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(multiMatchQuery)
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<ConceptoDocument> hits = elasticsearchOperations.search(nativeQuery, ConceptoDocument.class);
        return mapHits(hits, ConceptoHit.class, ConceptoDocument::getId);
    }

    private List<SearchHitDTO<FaqHit>> buscarFaqs(String q, int page, int size, List<String> indices) {
        if (!indices.contains("faqs")) return List.of();

        co.elastic.clients.elasticsearch._types.query_dsl.Query multiMatchQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(qb -> qb
                .multiMatch(mm -> mm
                        .query(q)
                        .fields("pregunta^3", "respuesta^2", "categoria")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(multiMatchQuery)
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<FaqDocument> hits = elasticsearchOperations.search(nativeQuery, FaqDocument.class);
        return mapHits(hits, FaqHit.class, FaqDocument::getId);
    }

    private <T, D> List<SearchHitDTO<T>> mapHits(SearchHits<D> hits, Class<T> hitClass, java.util.function.Function<D, String> idExtractor) {
        return hits.stream()
                .map(hit -> SearchHitDTO.of(
                        idExtractor.apply(hit.getContent()),
                        hit.getScore(),
                        (T) mapToHitClass(hit.getContent(), hitClass),
                        mapHighlight(hit)
                ))
                .collect(Collectors.toList());
    }

    private <D> Object mapToHitClass(D doc, Class<?> hitClass) {
        if (hitClass == CursoHit.class) {
            CursoDocument c = (CursoDocument) doc;
            return CursoHit.of(
                    c.getId(), c.getTitulo(), c.getDescripcion(), c.getInstructor(),
                    c.getNivel(), c.getIcon(), c.getColor(), c.getSemanas(),
                    c.getLecciones(), c.getPublicado()
            );
        } else if (hitClass == LeccionHit.class) {
            LeccionDocument l = (LeccionDocument) doc;
            return LeccionHit.of(
                    l.getId(), l.getCursoId(), l.getTitulo(), l.getDescripcion(),
                    l.getSeccionTitulo(), l.getTipo(), l.getDuracion()
            );
        } else if (hitClass == ConceptoHit.class) {
            ConceptoDocument c = (ConceptoDocument) doc;
            return ConceptoHit.of(
                    c.getId(), c.getTermino(), c.getDefinicion(),
                    c.getSinonimos(), c.getTemas()
            );
        } else if (hitClass == FaqHit.class) {
            FaqDocument f = (FaqDocument) doc;
            return FaqHit.of(
                    f.getId(), f.getPregunta(), f.getRespuesta(), f.getCategoria()
            );
        }
        return null;
    }

    private com.luxray.search.dto.HighlightDTO mapHighlight(SearchHit<?> hit) {
        if (hit.getHighlightFields() == null || hit.getHighlightFields().isEmpty()) {
            return null;
        }
        return com.luxray.search.dto.HighlightDTO.of(hit.getHighlightFields().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        e -> e.getValue()
                )));
    }

    // Métodos para indexación
    public void indexarCurso(CursoDocument doc) {
        cursoRepo.save(doc);
        log.debug("Curso indexado: {}", doc.getId());
    }

    public void indexarLeccion(LeccionDocument doc) {
        leccionRepo.save(doc);
        log.debug("Lección indexada: {}", doc.getId());
    }

    public void indexarConcepto(ConceptoDocument doc) {
        conceptoRepo.save(doc);
        log.debug("Concepto indexado: {}", doc.getId());
    }

    public void indexarFaq(FaqDocument doc) {
        faqRepo.save(doc);
        log.debug("FAQ indexada: {}", doc.getId());
    }

    public void eliminarCurso(String id) {
        cursoRepo.deleteById(id);
    }

    public void eliminarLeccion(String id) {
        leccionRepo.deleteById(id);
    }

    public void eliminarConcepto(String id) {
        conceptoRepo.deleteById(id);
    }

    public void eliminarFaq(String id) {
        faqRepo.deleteById(id);
    }
}