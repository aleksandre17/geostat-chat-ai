package com.geostat.ingestion.feedback;

import com.geostat.platform.feedback.ScoreBoostPolicy;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly aggregator that updates document.score_boost based on chat feedback.
 * Reads from chat.feedback + chat.retrieval_hit, writes to ingestion.document.
 */
@Service
@ConditionalOnProperty(
        prefix = "geostat.ingestion.feedback",
        name = "score-boost-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class FeedbackScoreAggregator {

    private static final Logger log = LoggerFactory.getLogger(FeedbackScoreAggregator.class);

    private final JdbcTemplate jdbc;
    private final ScoreBoostPolicy policy;

    public FeedbackScoreAggregator(JdbcTemplate jdbc, ScoreBoostPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    /**
     * Run nightly at 3 AM to aggregate feedback and update score_boost.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void aggregateScoreBoost() {
        log.info("Starting feedback score boost aggregation");
        List<FeedbackAggregateRow> aggregates = fetchAggregates();
        int updated = 0;

        for (FeedbackAggregateRow row : aggregates) {
            double delta = policy.calculateDelta(row.positiveCount, row.negativeCount, row.totalHits);
            if (Math.abs(delta) > 0.001) {
                updateScoreBoost(row.documentId, delta);
                updated++;
            }
        }

        log.info("Feedback aggregation complete: {} documents updated", updated);
    }

    @Transactional(readOnly = true)
    List<FeedbackAggregateRow> fetchAggregates() {
        return jdbc.query(
                """
                SELECT
                    rh.document_id,
                    COUNT(*) as total_hits,
                    COUNT(CASE WHEN f.rating = 'positive' THEN 1 END) as positive_count,
                    COUNT(CASE WHEN f.rating = 'negative' THEN 1 END) as negative_count
                FROM chat.retrieval_hit rh
                JOIN chat.turn t ON t.id = rh.turn_id
                LEFT JOIN chat.feedback f ON f.turn_id = t.id
                WHERE rh.document_id IS NOT NULL
                  AND t.created_at > NOW() - INTERVAL '7 days'
                GROUP BY rh.document_id
                HAVING COUNT(*) >= 10
                """,
                (rs, rowNum) -> new FeedbackAggregateRow(
                        UUID.fromString(rs.getString("document_id")),
                        rs.getInt("total_hits"),
                        rs.getInt("positive_count"),
                        rs.getInt("negative_count")));
    }

    @Transactional
    void updateScoreBoost(UUID documentId, double delta) {
        jdbc.update(
                """
                UPDATE ingestion.document
                SET score_boost = LEAST(2.0, GREATEST(0.5, COALESCE(score_boost, 1.0) + ?)),
                    updated_at = NOW()
                WHERE id = ?
                """,
                delta,
                documentId);
    }

    record FeedbackAggregateRow(UUID documentId, int totalHits, int positiveCount, int negativeCount) {}
}
