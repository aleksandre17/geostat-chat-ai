package com.geostat.ingestion.embed;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Operator-triggered job to re-embed all chunks that were embedded
 * with a different model than the current configured model.
 *
 * <p>Safe to re-run: idempotent — skips chunks already on current model.
 * Trigger: POST /admin/embedding/migrate (wired by operator, not auto-scheduled).
 */
@Component
@Profile("db")
public class ModelMigrationJob {

    private static final Logger log = LoggerFactory.getLogger(ModelMigrationJob.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingQueue embeddingQueue;

    @Value("${geostat.ingestion.embedding.model-id:gemini/text-embedding-004}")
    private String currentModelId;

    public ModelMigrationJob(JdbcTemplate jdbc, EmbeddingQueue embeddingQueue) {
        this.jdbc = jdbc;
        this.embeddingQueue = embeddingQueue;
    }

    public MigrationStats migrateToCurrentModel(UUID corpusId) {
        List<UUID> staleIds = jdbc.queryForList(
                """
                SELECT id FROM ingestion.chunk
                WHERE corpus_id       = ?
                  AND embedding_status = 'embedded'
                  AND (embedding_model IS NULL OR embedding_model != ?)
                """,
                UUID.class,
                corpusId,
                currentModelId);

        log.info(
                "[model-migration] {} chunks to re-embed for model {} in corpus {}",
                staleIds.size(),
                currentModelId,
                corpusId);

        int batchSize = 100;
        for (int i = 0; i < staleIds.size(); i += batchSize) {
            List<UUID> batch = staleIds.subList(i, Math.min(i + batchSize, staleIds.size()));
            embeddingQueue.enqueueAll(batch);
        }
        return new MigrationStats(staleIds.size(), currentModelId);
    }

    public record MigrationStats(int chunksToMigrate, String targetModel) {}
}
