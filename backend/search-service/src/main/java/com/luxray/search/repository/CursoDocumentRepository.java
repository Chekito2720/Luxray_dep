package com.luxray.search.repository;

import com.luxray.search.document.CursoDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CursoDocumentRepository extends ElasticsearchRepository<CursoDocument, String> {
}