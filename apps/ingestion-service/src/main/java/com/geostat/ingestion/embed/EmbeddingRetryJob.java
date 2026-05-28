package com.geostat.ingestion.embed;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class EmbeddingRetryJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRetryJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingQueue embeddingQueue;

    public EmbeddingRetryJob(JdbcTemplate jdbcTemplate, EmbeddingQueue embeddingQueue) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingQueue = embeddingQueue;
    }

    /**
     * Finds chunks that have been in embedding_status='pending' for &gt; 10 minutes (Qdrant call must
     * have failed or was never attempted). Re-enqueues them for embedding.
     *
     * <p>Scheduled: every 15 minutes. Safe to re-run: QdrantEmbeddingService uses upsert (not insert).
     */
    @Scheduled(fixedDelayString = "${ingestion.embedding.retry-interval-ms:900000}")
    public void retryStuckChunks() {
        List<UUID> stuckIds = jdbcTemplate.queryForList(
                """
                SELECT id FROM ingestion.chunk
                WHERE embedding_status = 'pending'
                  AND created_at < now() - INTERVAL '10 minutes'
                LIMIT 500
                """,
                UUID.class);

        if (stuckIds.isEmpty()) {
            return;
        }

        log.warn("[embedding-retry] {} chunks stuck in 'pending' — re-enqueueing", stuckIds.size());
        embeddingQueue.enqueueAll(stuckIds);
    }
}
