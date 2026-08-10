package com.luxray.search.repository;

import com.luxray.search.document.FaqDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FaqDocumentRepository extends ElasticsearchRepository<FaqDocument, String> {
}