package com.geostat.ingestion.feedback;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads feedback signal data from the chat schema.
 *
 * <p>TODO PHASE-D: Replace with FeedbackScoreEvent listener when chat service is decoupled.
 * This adapter isolates the cross-schema dependency in one place.
 * TEMPORARY — must be removed when chat service owns this query.
 */
@Component
public class ChatFeedbackReader {

    private final JdbcTemplate jdbc;

    public ChatFeedbackReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<FeedbackAggregateRow> fetchAggregates() {
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

    public record FeedbackAggregateRow(UUID documentId, int totalHits, int positiveCount, int negativeCount) {}
}
