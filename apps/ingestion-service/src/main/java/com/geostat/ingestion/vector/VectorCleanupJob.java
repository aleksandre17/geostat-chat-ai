package com.geostat.ingestion.vector;

import com.geostat.ingestion.index.qdrant.QdrantVectorStore;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.qdrant.VectorCollectionNaming;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class VectorCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VectorCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final QdrantVectorStore qdrant;
    private final CorpusRepository corpusRepository;

    public VectorCleanupJob(
            JdbcTemplate jdbc, QdrantVectorStore qdrant, CorpusRepository corpusRepository) {
        this.jdbc = jdbc;
        this.qdrant = qdrant;
        this.corpusRepository = corpusRepository;
    }

    /**
     * Deletes Qdrant vectors for chunks that:
     *
     * <ul>
     *   <li>are embedded ({@code embedding_status='embedded'}), but
     *   <li>their document is now excluded from MV (skip/rejected quality)
     * </ul>
     *
     * <p>Run after: V19 migration, QualityGateRunner, MV REFRESH. Safe to re-run (idempotent).
     */
    public CleanupResult cleanOrphanVectors(UUID corpusId) {
        List<String> orphanChunkIds = jdbc.queryForList(
                """
                SELECT c.id::text
                FROM ingestion.chunk c
                JOIN ingestion.document d ON d.id = c.document_id
                WHERE c.corpus_id        = ?
                  AND c.embedding_status = 'embedded'
                  AND (
                    d.quality_score NOT IN ('good', 'degraded')
                    OR d.page_kind = 'portal'
                    OR COALESCE(length(d.content_text), 0) < 100
                  )
                """,
                String.class,
                corpusId);

        if (orphanChunkIds.isEmpty()) {
            log.info("[vector-cleanup] corpus={} — no orphan vectors", corpusId);
            return CleanupResult.empty(corpusId);
        }

        log.info(
                "[vector-cleanup] corpus={} — deleting {} orphan vectors",
                corpusId,
                orphanChunkIds.size());

        CorpusEntity corpus = corpusRepository.findById(corpusId).orElseThrow();
        String collectionName = VectorCollectionNaming.collectionForName(corpus.getName());
        qdrant.delete(collectionName, orphanChunkIds);

        markChunksDeleted(orphanChunkIds);

        log.info(
                "[vector-cleanup] corpus={} — {} vectors deleted from Qdrant",
                corpusId,
                orphanChunkIds.size());
        return new CleanupResult(corpusId, orphanChunkIds.size());
    }

    private void markChunksDeleted(List<String> orphanChunkIds) {
        String placeholders =
                String.join(",", orphanChunkIds.stream().map(id -> "?").toList());
        Object[] args =
                orphanChunkIds.stream().map(UUID::fromString).toArray();
        jdbc.update(
                """
                UPDATE ingestion.chunk
                SET embedding_status = 'deleted'
                WHERE id IN (%s)
                """
                        .formatted(placeholders),
                args);
    }

    public record CleanupResult(UUID corpusId, int deletedCount) {
        public static CleanupResult empty(UUID id) {
            return new CleanupResult(id, 0);
        }
    }
}
