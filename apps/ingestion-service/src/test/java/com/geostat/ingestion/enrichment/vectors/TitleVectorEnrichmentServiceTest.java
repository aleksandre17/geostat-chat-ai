package com.geostat.ingestion.enrichment.vectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.embedding.EmbeddingPort;
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
import com.geostat.platform.enrichment.DocumentVectorWriter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TitleVectorEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private DocumentVectorWriter documentVectorWriter;

    private TitleVectorEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setTitleVectorModelVersion("hash-v1-title-v1@2026-05-25");
        when(embeddingPort.embed("Statistics page")).thenReturn(new float[] {0.1f, 0.2f});
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new TitleVectorEnrichmentService(executor, embeddingPort, documentVectorWriter, properties);
    }

    @Test
    void enrichDocumentEmbedsTitleAndWritesNamedVector() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.title_vector,
                        "hash-v1-title-v1@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId,
                        EnrichmentDeriverKind.title_vector,
                        "hash-v1-title-v1@2026-05-25"))
                .thenReturn(Optional.empty());

        service.enrichDocument(documentId);

        verify(documentVectorWriter)
                .writeNamedVector(documentId, NamedVectorNames.TITLE, new float[] {0.1f, 0.2f});

        ArgumentCaptor<EnrichmentRunEntity> runCaptor = ArgumentCaptor.forClass(EnrichmentRunEntity.class);
        verify(enrichmentRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().map(EnrichmentRunEntity::getStatus))
                .contains(EnrichmentRunStatus.completed);
    }

    private static DocumentEntity parsedDocument(UUID id) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setTitle("Statistics page");
        document.setLanguage("ka");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
