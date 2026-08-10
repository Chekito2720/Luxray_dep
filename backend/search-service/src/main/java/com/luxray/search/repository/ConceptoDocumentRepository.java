package com.luxray.search.repository;

import com.luxray.search.document.ConceptoDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ConceptoDocumentRepository extends ElasticsearchRepository<ConceptoDocument, String> {
}