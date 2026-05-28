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
 * Aggregates chat feedback signals to update document score_boost.
 * Currently DISABLED ({@code @ConditionalOnProperty matchIfMissing=false}).
 *
 * <p>TODO PHASE-D: Redesign with event-based architecture:
 * <ol>
 *   <li>chat-service publishes FeedbackScoreEvent</li>
 *   <li>ingestion-service listens and writes to curation_override</li>
 *   <li>Remove score_boost column (V22 migration)</li>
 * </ol>
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
    private final ChatFeedbackReader chatFeedbackReader;
    private final ScoreBoostPolicy policy;

    public FeedbackScoreAggregator(
            JdbcTemplate jdbc, ChatFeedbackReader chatFeedbackReader, ScoreBoostPolicy policy) {
        this.jdbc = jdbc;
        this.chatFeedbackReader = chatFeedbackReader;
        this.policy = policy;
    }

    /**
     * Run nightly at 3 AM to aggregate feedback and update score_boost.
     *
     * @deprecated Replaced by {@link com.geostat.ingestion.events.rabbit.FeedbackScoreListener}
     *     (event-driven via RabbitMQ). Remove after verifying FeedbackScoreListener processes all
     *     feedback correctly.
     */
    @Deprecated
    @Scheduled(cron = "0 0 3 * * *")
    public void aggregateScoreBoost() {
        log.info("Starting feedback score boost aggregation");
        List<ChatFeedbackReader.FeedbackAggregateRow> aggregates = chatFeedbackReader.fetchAggregates();
        int updated = 0;

        for (ChatFeedbackReader.FeedbackAggregateRow row : aggregates) {
            double delta = policy.calculateDelta(row.positiveCount(), row.negativeCount(), row.totalHits());
            if (Math.abs(delta) > 0.001) {
                updateScoreBoost(row.documentId(), delta);
                updated++;
            }
        }

        log.info("Feedback aggregation complete: {} documents updated", updated);
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
}
