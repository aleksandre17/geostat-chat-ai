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

    public DocumentEnrichmentOrchestrator(
            SummaryEnrichmentService summaryEnrichmentService,
            LocalePairEnrichmentService localePairEnrichmentService,
            KeywordEnrichmentService keywordEnrichmentService,
            EntityEnrichmentService entityEnrichmentService,
            PageKindEnrichmentService pageKindEnrichmentService,
            TitleVectorEnrichmentService titleVectorEnrichmentService,
            SummaryVectorEnrichmentService summaryVectorEnrichmentService,
            TopicAssignEnrichmentService topicAssignEnrichmentService,
            DocumentQdrantLifecycleSync documentQdrantLifecycleSync) {
        this.summaryEnrichmentService = summaryEnrichmentService;
        this.localePairEnrichmentService = localePairEnrichmentService;
        this.keywordEnrichmentService = keywordEnrichmentService;
        this.entityEnrichmentService = entityEnrichmentService;
        this.pageKindEnrichmentService = pageKindEnrichmentService;
        this.titleVectorEnrichmentService = titleVectorEnrichmentService;
        this.summaryVectorEnrichmentService = summaryVectorEnrichmentService;
        this.topicAssignEnrichmentService = topicAssignEnrichmentService;
        this.documentQdrantLifecycleSync = documentQdrantLifecycleSync;
    }

    @Transactional
    public void enrichDocument(UUID documentId) {
        enrichDocument(documentId, true);
    }

    /** P1 cutover backfill — gate derivers only; skip Gemini entities (not an eval gate, high parse-failure rate). */
    @Transactional
    public void enrichDocumentForBackfill(UUID documentId) {
        enrichDocument(documentId, false);
    }

    private void enrichDocument(UUID documentId, boolean includeEntities) {
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
        documentQdrantLifecycleSync.syncDocument(documentId);
    }
}
