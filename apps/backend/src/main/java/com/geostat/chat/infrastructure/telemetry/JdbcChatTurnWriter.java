package com.geostat.chat.infrastructure.telemetry;

import com.geostat.chat.application.telemetry.ChatFeedbackCommand;
import com.geostat.chat.application.telemetry.ChatTurnWriter;
import com.geostat.chat.application.telemetry.RetrievalHit;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.telemetry", name = "store", havingValue = "jdbc")
public class JdbcChatTurnWriter implements ChatTurnWriter {

    private final JdbcTemplate jdbc;

    public JdbcChatTurnWriter(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void recordTurn(
            String turnId,
            String sessionId,
            String userMessage,
            List<RetrievalHit> hits,
            int promptVersion,
            String promptHash) {
        UUID id = UUID.fromString(turnId);
        jdbc.update(
                """
                INSERT INTO chat.turn (id, session_id, user_message, prompt_version, prompt_hash)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                id,
                sessionId,
                userMessage,
                promptVersion,
                promptHash);
        for (RetrievalHit hit : hits) {
            jdbc.update(
                    """
                    INSERT INTO chat.retrieval_hit (turn_id, document_id, source_url, score)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (turn_id, document_id) DO NOTHING
                    """,
                    id,
                    hit.documentId(),
                    hit.sourceUrl(),
                    hit.score());
        }
    }

    @Override
    public void recordFeedback(ChatFeedbackCommand command) {
        jdbc.update(
                """
                INSERT INTO chat.feedback (turn_id, session_id, rating, comment)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (turn_id) DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment
                """,
                UUID.fromString(command.turnId()),
                command.sessionId(),
                command.rating(),
                command.comment());
    }
}
