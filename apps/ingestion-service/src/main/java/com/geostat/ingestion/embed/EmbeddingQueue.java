package com.geostat.ingestion.embed;

import com.geostat.ingestion.events.DocumentIndexTrigger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Enqueues chunk embeddings via the document index trigger (async, retriable). */
@Component
@Profile("db")
public class EmbeddingQueue {

    private final DocumentIndexTrigger documentIndexTrigger;
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingQueue(DocumentIndexTrigger documentIndexTrigger, JdbcTemplate jdbcTemplate) {
        this.documentIndexTrigger = documentIndexTrigger;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(List<UUID> chunkIds) {
        enqueueAll(chunkIds);
    }

    public void enqueueAll(List<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", chunkIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT document_id, corpus_id
                FROM ingestion.chunk
                WHERE id IN (%s)
                """
                        .formatted(placeholders),
                chunkIds.toArray());
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            UUID documentId = (UUID) row.get("document_id");
            UUID corpusId = (UUID) row.get("corpus_id");
            String key = documentId + ":" + corpusId;
            if (seen.add(key)) {
                documentIndexTrigger.requestIndex(documentId, corpusId);
            }
        }
    }
}
