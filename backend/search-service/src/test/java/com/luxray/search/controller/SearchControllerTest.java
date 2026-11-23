package com.luxray.search.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.search.document.ConceptoDocument;
import com.luxray.search.document.CursoDocument;
import com.luxray.search.document.FaqDocument;
import com.luxray.search.document.LeccionDocument;
import com.luxray.search.repository.ConceptoDocumentRepository;
import com.luxray.search.repository.CursoDocumentRepository;
import com.luxray.search.repository.FaqDocumentRepository;
import com.luxray.search.repository.LeccionDocumentRepository;
import com.luxray.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock private SearchService searchService;
    @Mock private CursoDocumentRepository cursoRepo;
    @Mock private LeccionDocumentRepository leccionRepo;
    @Mock private ConceptoDocumentRepository conceptoRepo;
    @Mock private FaqDocumentRepository faqRepo;

    @InjectMocks private SearchController controller;

    @Test
    void reindexDeshabilitadoDevuelve403YNoEscribe() {
        ReflectionTestUtils.setField(controller, "reindexEnabled", false);

        assertThatThrownBy(controller::reindex)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(cursoRepo, leccionRepo, conceptoRepo, faqRepo);
    }

    @Test
    void reindexHabilitadoConservaLaCargaDemo() {
        ReflectionTestUtils.setField(controller, "reindexEnabled", true);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.reindex();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(cursoRepo).save(any(CursoDocument.class));
        verify(leccionRepo).save(any(LeccionDocument.class));
        verify(conceptoRepo, times(6)).save(any(ConceptoDocument.class));
        verify(faqRepo).save(any(FaqDocument.class));
    }
}
