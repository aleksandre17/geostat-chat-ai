package com.geostat.ingestion.index.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServeStateResolverTest {

    @Mock
    private CurationOverrideRepository curationOverrideRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    private DocumentServeStateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DocumentServeStateResolver(curationOverrideRepository, enrichmentRunRepository);
    }

    @Test
    void excludedUrlIsDropped() {
        DocumentEntity document = parsedDocument();
        when(curationOverrideRepository.isUrlExcluded(eq(document.getUrlHash()), any(Instant.class)))
                .thenReturn(true);

        assertThat(resolver.resolve(document)).isEqualTo(DocumentServeState.DROPPED);
    }

    @Test
    void parsedDocumentWithoutEnrichmentRunsIsLive() {
        DocumentEntity document = parsedDocument();
        when(curationOverrideRepository.isUrlExcluded(eq(document.getUrlHash()), any(Instant.class)))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_Id(document.getId())).thenReturn(List.of());

        assertThat(resolver.resolve(document)).isEqualTo(DocumentServeState.LIVE);
    }

    @Test
    void mandatoryEnrichmentCompleteIsLive() {
        DocumentEntity document = parsedDocument();
        document.setPageKind("portal");
        document.setTopicClusterId(UUID.randomUUID());
        when(curationOverrideRepository.isUrlExcluded(eq(document.getUrlHash()), any(Instant.class)))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_Id(document.getId()))
                .thenReturn(List.of(
                        completedRun(EnrichmentDeriverKind.summary),
                        completedRun(EnrichmentDeriverKind.page_kind),
                        completedRun(EnrichmentDeriverKind.topic_assign)));

        assertThat(resolver.resolve(document)).isEqualTo(DocumentServeState.LIVE);
    }

    private static DocumentEntity parsedDocument() {
        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        document.setUrlHash("hash");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }

    private static EnrichmentRunEntity completedRun(EnrichmentDeriverKind kind) {
        EnrichmentRunEntity run = new EnrichmentRunEntity();
        run.setDeriverKind(kind);
        run.setStatus(EnrichmentRunStatus.completed);
        return run;
    }
}
