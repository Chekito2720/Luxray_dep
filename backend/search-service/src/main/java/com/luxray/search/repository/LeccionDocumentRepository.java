package com.luxray.search.repository;

import com.luxray.search.document.LeccionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface LeccionDocumentRepository extends ElasticsearchRepository<LeccionDocument, String> {
}