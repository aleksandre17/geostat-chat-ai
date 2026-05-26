package com.geostat.ingestion.enrichment.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.KeywordDeriver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeywordEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private KeywordDeriver keywordDeriver;

    private KeywordEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setKeywordModelVersion("yake-v1");
        properties.setKeywordTopN(15);
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new KeywordEnrichmentService(executor, keywordDeriver, properties);
    }

    @Test
    void enrichDocumentPersistsKeywordsAndCompletedRun() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId, "ka", "CPI inflation 2024 statistics");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId, EnrichmentDeriverKind.keywords, "yake-v1", EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId, EnrichmentDeriverKind.keywords, "yake-v1"))
                .thenReturn(Optional.empty());
        when(keywordDeriver.deriveKeywords(any(DocumentContext.class), eq(15)))
                .thenReturn(List.of("CPI", "inflation", "2024"));

        service.enrichDocument(documentId);

        assertThat(document.getKeywords()).containsExactly("CPI", "inflation", "2024");
        verify(documentRepository).save(document);

        ArgumentCaptor<EnrichmentRunEntity> runCaptor = ArgumentCaptor.forClass(EnrichmentRunEntity.class);
        verify(enrichmentRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().map(EnrichmentRunEntity::getStatus))
                .contains(EnrichmentRunStatus.completed);
    }

    @Test
    void enrichDocumentMergesLocalePairKeywords() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId, "ka", "ფასების ინფლაცია");
        DocumentEntity localePair = parsedDocument(UUID.randomUUID(), "en", "Consumer Price Index CPI");
        document.setLocalePairDocument(localePair);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId, EnrichmentDeriverKind.keywords, "yake-v1", EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId, EnrichmentDeriverKind.keywords, "yake-v1"))
                .thenReturn(Optional.empty());
        when(keywordDeriver.deriveKeywords(any(DocumentContext.class), eq(15)))
                .thenReturn(List.of("ფასების"))
                .thenReturn(List.of("CPI", "Consumer Price Index"));

        service.enrichDocument(documentId);

        assertThat(document.getKeywords()).containsExactly("ფასების", "CPI", "Consumer Price Index");
    }

    @Test
    void enrichDocumentSkipsWhenAlreadyCompleted() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId, "ka", "CPI inflation");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId, EnrichmentDeriverKind.keywords, "yake-v1", EnrichmentRunStatus.completed))
                .thenReturn(true);

        service.enrichDocument(documentId);

        verify(keywordDeriver, never()).deriveKeywords(any(), eq(15));
    }

    @Test
    void mergeKeywordsDedupesCaseInsensitive() {
        assertThat(KeywordEnrichmentService.mergeKeywords(
                        List.of("CPI", "Inflation"), List.of("cpi", "statistics")))
                .containsExactly("CPI", "Inflation", "statistics");
    }

    private static DocumentEntity parsedDocument(UUID id, String language, String content) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        corpus.setName("geostat-portal");
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setCanonicalUrl("https://www.geostat.ge/" + language + "/statistics");
        document.setUrlHash("hash-" + id);
        document.setTitle("Statistics");
        document.setLanguage(language);
        document.setContentText(content);
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
