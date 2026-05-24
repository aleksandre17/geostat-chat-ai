package com.geostat.chat.application.telemetry;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.stereotype.Service;

/** Structured chat turn + feedback (B-19 / B-07). */
@Service
public class ChatTelemetryService {

    private final TurnRegistry turnRegistry;
    private final ChatTurnWriter turnWriter;

    public ChatTelemetryService(TurnRegistry turnRegistry, ChatTurnWriter turnWriter) {
        this.turnRegistry = turnRegistry;
        this.turnWriter = turnWriter;
    }

    public List<RetrievalHit> toHits(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(c -> new RetrievalHit(c.documentId(), c.sourceUrl(), c.score()))
                .toList();
    }

    public void recordTurn(
            String turnId,
            String sessionId,
            String userMessage,
            List<RetrievalHit> hits,
            int promptVersion,
            String promptHash) {
        turnRegistry.register(turnId, sessionId);
        turnWriter.recordTurn(turnId, sessionId, userMessage, hits, promptVersion, promptHash);
    }

    public void recordFeedback(ChatFeedbackCommand command) {
        if (command == null || command.turnId() == null || command.turnId().isBlank()) {
            throw new IllegalArgumentException("turnId required");
        }
        String rating = normalizeRating(command.rating());
        turnWriter.recordFeedback(new ChatFeedbackCommand(
                command.turnId(), command.sessionId(), rating, command.comment()));
    }

    private static String normalizeRating(String rating) {
        if (rating == null || rating.isBlank()) {
            throw new IllegalArgumentException("rating required (up or down)");
        }
        return switch (rating.trim().toLowerCase()) {
            case "up", "positive", "1", "thumbs_up" -> "up";
            case "down", "negative", "-1", "thumbs_down" -> "down";
            default -> throw new IllegalArgumentException("rating must be up or down");
        };
    }
}
