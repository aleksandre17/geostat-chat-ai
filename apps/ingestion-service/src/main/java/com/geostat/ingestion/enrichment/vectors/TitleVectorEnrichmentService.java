package com.geostat.ingestion.enrichment.vectors;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.platform.enrichment.DocumentVectorWriter;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class TitleVectorEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final EmbeddingPort embeddingPort;
    private final DocumentVectorWriter documentVectorWriter;
    private final EnrichmentProperties properties;

    public TitleVectorEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            EmbeddingPort embeddingPort,
            DocumentVectorWriter documentVectorWriter,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.embeddingPort = embeddingPort;
        this.documentVectorWriter = documentVectorWriter;
        this.properties = properties;
    }

    @Transactional
    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.titleVectorModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.title_vector,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                this::writeTitleVector,
                (document, ignored) -> {},
                "title_vector");
    }

    private boolean shouldSkip(DocumentEntity document) {
        return document.getTitle() == null || document.getTitle().isBlank();
    }

    private Integer writeTitleVector(DocumentEntity document) {
        float[] embedding = embeddingPort.embed(document.getTitle().trim());
        documentVectorWriter.writeNamedVector(document.getId(), NamedVectorNames.TITLE, embedding);
        return embedding.length;
    }
}
