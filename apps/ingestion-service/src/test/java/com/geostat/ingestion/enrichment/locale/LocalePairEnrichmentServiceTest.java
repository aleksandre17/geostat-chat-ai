package com.geostat.ingestion.enrichment.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.LocalePairDeriver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalePairEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private LocalePairDeriver localePairDeriver;

    @Mock
    private DocumentLocalePairLinker localePairLinker;

    private LocalePairEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setLocalePairModelVersion("url-embed-v1@2026-05-25");
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new LocalePairEnrichmentService(
                executor, localePairDeriver, localePairLinker, documentRepository, properties);
    }

    @Test
    void enrichDocumentSetsBidirectionalPairAndCompletedRun() {
        UUID documentId = UUID.randomUUID();
        UUID pairId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId, corpusId, "ka");
        DocumentEntity counterpart = parsedDocument(pairId, corpusId, "en");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.findById(pairId)).thenReturn(Optional.of(counterpart));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.locale_pair,
                        "url-embed-v1@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId,
                        EnrichmentDeriverKind.locale_pair,
                        "url-embed-v1@2026-05-25"))
                .thenReturn(Optional.empty());
        when(localePairDeriver.findPair(any(DocumentContext.class))).thenReturn(Optional.of(pairId));

        service.enrichDocument(documentId);

        assertThat(document.getLocalePairDocument()).isSameAs(counterpart);
        assertThat(counterpart.getLocalePairDocument()).isSameAs(document);
        verify(localePairLinker)
                .link(corpusId, documentId, document.getCanonicalUrl(), document.getLanguage());
        verify(documentRepository).updateLocalePairDocId(documentId, pairId);
        verify(documentRepository).updateLocalePairDocId(pairId, documentId);
        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<EnrichmentRunEntity> runCaptor = ArgumentCaptor.forClass(EnrichmentRunEntity.class);
        verify(enrichmentRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().map(EnrichmentRunEntity::getStatus))
                .contains(EnrichmentRunStatus.completed);
    }

    @Test
    void enrichDocumentSkipsDeriverLookupWhenAlreadyCompleted() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId, UUID.randomUUID(), "ka");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.locale_pair,
                        "url-embed-v1@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(true);

        service.enrichDocument(documentId);

        verify(localePairDeriver, never()).findPair(any());
    }

    private static DocumentEntity parsedDocument(UUID id, UUID corpusId, String language) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setCanonicalUrl("https://www.geostat.ge/" + language + "/modules/41");
        document.setLanguage(language);
        document.setTitle("Statistics");
        document.setContentText("CPI 2024");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
