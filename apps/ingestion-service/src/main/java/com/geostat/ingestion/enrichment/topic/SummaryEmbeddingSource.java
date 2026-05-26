package com.geostat.ingestion.enrichment.topic;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.ingestion.enrichment.vectors.DocumentSummaryText;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SummaryEmbeddingSource {

    private final EmbeddingPort embeddingPort;

    public SummaryEmbeddingSource(EmbeddingPort embeddingPort) {
        this.embeddingPort = embeddingPort;
    }

    public Map<UUID, float[]> embedDocuments(Iterable<DocumentEntity> documents) {
        Map<UUID, float[]> vectors = new HashMap<>();
        for (DocumentEntity document : documents) {
            String summary = DocumentSummaryText.pickForEmbedding(document);
            if (summary.isBlank()) {
                continue;
            }
            vectors.put(document.getId(), embeddingPort.embed(summary));
        }
        return vectors;
    }

    public float[] embedDocument(DocumentEntity document) {
        String summary = DocumentSummaryText.pickForEmbedding(document);
        if (summary.isBlank()) {
            return new float[0];
        }
        return embeddingPort.embed(summary);
    }
}
