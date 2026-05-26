package com.geostat.ingestion.enrichment.vectors;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import com.geostat.platform.enrichment.DocumentVectorWriter;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class SummaryVectorEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final EmbeddingPort embeddingPort;
    private final DocumentVectorWriter documentVectorWriter;
    private final EnrichmentRunRepository enrichmentRunRepository;
    private final EnrichmentProperties properties;

    public SummaryVectorEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            EmbeddingPort embeddingPort,
            DocumentVectorWriter documentVectorWriter,
            EnrichmentRunRepository enrichmentRunRepository,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.embeddingPort = embeddingPort;
        this.documentVectorWriter = documentVectorWriter;
        this.enrichmentRunRepository = enrichmentRunRepository;
        this.properties = properties;
    }

    @Transactional
    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.summaryVectorModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.summary_vector,
                modelVersion,
                properties.maxRetries(),
                document -> shouldSkip(documentId, document),
                this::writeSummaryVector,
                (document, ignored) -> {},
                "summary_vector");
    }

    private boolean shouldSkip(UUID documentId, DocumentEntity document) {
        if (DocumentSummaryText.pickForEmbedding(document).isBlank()) {
            return true;
        }
        return !enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                documentId,
                EnrichmentDeriverKind.summary,
                properties.modelVersion(),
                EnrichmentRunStatus.completed);
    }

    private Integer writeSummaryVector(DocumentEntity document) {
        String summaryText = DocumentSummaryText.pickForEmbedding(document);
        float[] embedding = embeddingPort.embed(summaryText);
        documentVectorWriter.writeNamedVector(document.getId(), NamedVectorNames.SUMMARY, embedding);
        return embedding.length;
    }
}
