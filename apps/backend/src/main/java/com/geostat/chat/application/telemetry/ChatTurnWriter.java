package com.geostat.chat.application.telemetry;

import java.util.List;

/** Persists chat turns and feedback (log default, JDBC optional profile telemetry-db). */
public interface ChatTurnWriter {

    void recordTurn(
            String turnId,
            String sessionId,
            String userMessage,
            List<RetrievalHit> hits,
            int promptVersion,
            String promptHash);

    void recordFeedback(ChatFeedbackCommand command);
}
