package com.geostat.chat.application.telemetry;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.telemetry", name = "store", havingValue = "log", matchIfMissing = true)
public class LoggingChatTurnWriter implements ChatTurnWriter {

    private static final Logger TELEMETRY = LoggerFactory.getLogger("ChatTelemetry");

    @Override
    public void recordTurn(
            String turnId,
            String sessionId,
            String userMessage,
            List<RetrievalHit> hits,
            int promptVersion,
            String promptHash) {
        TELEMETRY.info(
                "event=chat_turn turnId={} sessionId={} hitCount={} queryLen={} promptVersion={} promptHash={}",
                turnId,
                sessionId,
                hits.size(),
                userMessage != null ? userMessage.length() : 0,
                promptVersion,
                promptHash != null ? promptHash : "");
        for (RetrievalHit hit : hits) {
            TELEMETRY.info(
                    "event=retrieval_hit turnId={} documentId={} sourceUrl={} score={}",
                    turnId,
                    hit.documentId(),
                    hit.sourceUrl(),
                    hit.score());
        }
    }

    @Override
    public void recordFeedback(ChatFeedbackCommand command) {
        TELEMETRY.info(
                "event=chat_feedback turnId={} sessionId={} rating={} commentLen={}",
                command.turnId(),
                command.sessionId(),
                command.rating(),
                command.comment() != null ? command.comment().length() : 0);
    }
}
