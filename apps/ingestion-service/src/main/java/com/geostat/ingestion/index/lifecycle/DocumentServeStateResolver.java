package com.geostat.ingestion.index.lifecycle;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class DocumentServeStateResolver {

    private final CurationOverrideRepository curationOverrideRepository;
    private final EnrichmentRunRepository enrichmentRunRepository;

    public DocumentServeStateResolver(
            CurationOverrideRepository curationOverrideRepository,
            EnrichmentRunRepository enrichmentRunRepository) {
        this.curationOverrideRepository = curationOverrideRepository;
        this.enrichmentRunRepository = enrichmentRunRepository;
    }

    public DocumentServeState resolve(DocumentEntity document) {
        if (document == null) {
            return DocumentServeState.DROPPED;
        }
        if (document.getFetchStatus() != DocumentFetchStatus.parsed) {
            return DocumentServeState.DROPPED;
        }
        if (curationOverrideRepository.isUrlExcluded(document.getUrlHash(), Instant.now())) {
            return DocumentServeState.DROPPED;
        }

        List<EnrichmentRunEntity> runs = enrichmentRunRepository.findByDocument_Id(document.getId());
        if (runs.isEmpty()) {
            return DocumentServeState.LIVE;
        }

        boolean summaryOk = isCompleted(runs, EnrichmentDeriverKind.summary);
        boolean pageKindOk =
                isCompleted(runs, EnrichmentDeriverKind.page_kind) || !"unknown".equals(document.getPageKind());
        boolean topicOk =
                document.getTopicClusterId() != null || isCompleted(runs, EnrichmentDeriverKind.topic_assign);

        if (summaryOk && pageKindOk && topicOk) {
            return DocumentServeState.LIVE;
        }
        if (summaryOk) {
            return DocumentServeState.PARTIAL_ENRICHED;
        }
        return DocumentServeState.PARTIAL_ENRICHED;
    }

    private static boolean isCompleted(List<EnrichmentRunEntity> runs, EnrichmentDeriverKind kind) {
        return runs.stream()
                .anyMatch(run -> run.getDeriverKind() == kind && run.getStatus() == EnrichmentRunStatus.completed);
    }
}
