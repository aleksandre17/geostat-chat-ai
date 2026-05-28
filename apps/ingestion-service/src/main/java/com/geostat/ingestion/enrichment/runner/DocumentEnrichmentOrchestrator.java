package com.geostat.ingestion.enrichment.runner;

import com.geostat.ingestion.enrichment.entity.EntityEnrichmentService;
import com.geostat.ingestion.enrichment.keyword.KeywordEnrichmentService;
import com.geostat.ingestion.enrichment.locale.LocalePairEnrichmentService;
import com.geostat.ingestion.enrichment.pagekind.PageKindEnrichmentService;
import com.geostat.ingestion.index.lifecycle.DocumentQdrantLifecycleSync;
import com.geostat.ingestion.enrichment.topic.TopicAssignEnrichmentService;
import com.geostat.ingestion.enrichment.summary.SummaryEnrichmentService;
import com.geostat.ingestion.enrichment.vectors.SummaryVectorEnrichmentService;
import com.geostat.ingestion.enrichment.vectors.TitleVectorEnrichmentService;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class DocumentEnrichmentOrchestrator {

    private final SummaryEnrichmentService summaryEnrichmentService;
    private final LocalePairEnrichmentService localePairEnrichmentService;
    private final KeywordEnrichmentService keywordEnrichmentService;
    private final EntityEnrichmentService entityEnrichmentService;
    private final PageKindEnrichmentService pageKindEnrichmentService;
    private final TitleVectorEnrichmentService titleVectorEnrichmentService;
    private final SummaryVectorEnrichmentService summaryVectorEnrichmentService;
    private final TopicAssignEnrichmentService topicAssignEnrichmentService;
    private final DocumentQdrantLifecycleSync documentQdrantLifecycleSync;
    private final DocumentRepository documentRepository;

    public DocumentEnrichmentOrchestrator(
            SummaryEnrichmentService summaryEnrichmentService,
            LocalePairEnrichmentService localePairEnrichmentService,
            KeywordEnrichmentService keywordEnrichmentService,
            EntityEnrichmentService entityEnrichmentService,
            PageKindEnrichmentService pageKindEnrichmentService,
            TitleVectorEnrichmentService titleVectorEnrichmentService,
            SummaryVectorEnrichmentService summaryVectorEnrichmentService,
            TopicAssignEnrichmentService topicAssignEnrichmentService,
            DocumentQdrantLifecycleSync documentQdrantLifecycleSync,
            DocumentRepository documentRepository) {
        this.summaryEnrichmentService = summaryEnrichmentService;
        this.localePairEnrichmentService = localePairEnrichmentService;
        this.keywordEnrichmentService = keywordEnrichmentService;
        this.entityEnrichmentService = entityEnrichmentService;
        this.pageKindEnrichmentService = pageKindEnrichmentService;
        this.titleVectorEnrichmentService = titleVectorEnrichmentService;
        this.summaryVectorEnrichmentService = summaryVectorEnrichmentService;
        this.topicAssignEnrichmentService = topicAssignEnrichmentService;
        this.documentQdrantLifecycleSync = documentQdrantLifecycleSync;
        this.documentRepository = documentRepository;
    }

    /**
     * Full enrichment: summary + keywords + entities + vectors + topic + Qdrant sync.
     *
     * Three-phase pattern to avoid holding DB connection during LLM calls:
     *   Phase 1 — LOAD  (short @Transactional ~10ms): read document data
     *   Phase 2 — ENRICH (no @Transactional ~4–6s): all external calls
     *   Phase 3 — SAVE  (short @Transactional ~20ms): persist results
     */
    public void enrichDocument(UUID documentId) {
        runEnrichment(documentId, true);
    }

    /** P1 cutover backfill — gate derivers only; skip Gemini entities (not an eval gate, high parse-failure rate). */
    public void enrichDocumentForBackfill(UUID documentId) {
        runEnrichment(documentId, false);
    }

    private void runEnrichment(UUID documentId, boolean includeEntities) {
        EnrichmentInput input = loadInput(documentId);
        EnrichmentResults results = computeEnrichments(input, includeEntities);
        persistResults(documentId, results);
        documentQdrantLifecycleSync.syncDocument(documentId);
    }

    @Transactional(readOnly = true)
    EnrichmentInput loadInput(UUID documentId) {
        return new EnrichmentInput(documentId);
    }

    EnrichmentResults computeEnrichments(EnrichmentInput input, boolean includeEntities) {
        UUID documentId = input.documentId();
        summaryEnrichmentService.enrichDocument(documentId);
        localePairEnrichmentService.enrichDocument(documentId);
        keywordEnrichmentService.enrichDocument(documentId);
        if (includeEntities) {
            entityEnrichmentService.enrichDocument(documentId);
        }
        pageKindEnrichmentService.enrichDocument(documentId);
        titleVectorEnrichmentService.enrichDocument(documentId);
        summaryVectorEnrichmentService.enrichDocument(documentId);
        topicAssignEnrichmentService.enrichDocument(documentId);
        return new EnrichmentResults();
    }

    @Transactional
    void persistResults(UUID documentId, EnrichmentResults results) {
        documentRepository.incrementEnrichmentVersion(documentId);
    }

    record EnrichmentInput(UUID documentId) {}

    record EnrichmentResults() {}
}
