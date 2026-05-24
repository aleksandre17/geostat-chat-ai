package com.geostat.chat.application.telemetry;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatTelemetryServiceTest {

    @Test
    void toHits_mapsChunks() {
        ChatTelemetryService svc = new ChatTelemetryService(new TurnRegistry(), new LoggingChatTurnWriter());
        List<RetrievalHit> hits = svc.toHits(List.of(
                new RetrievedChunk("doc-1", "https://www.geostat.ge/ka", "text", 0.91)));
        assertEquals(1, hits.size());
        assertEquals("doc-1", hits.get(0).documentId());
        assertEquals(0.91, hits.get(0).score());
    }

    @Test
    void recordFeedback_normalizesRating() {
        ChatTelemetryService svc = new ChatTelemetryService(new TurnRegistry(), new LoggingChatTurnWriter());
        svc.recordTurn("turn-1", "sess-1", "hello", List.of(), 1, "hash");
        svc.recordFeedback(new ChatFeedbackCommand("turn-1", "sess-1", "thumbs_up", null));
    }

    @Test
    void recordFeedback_rejectsInvalidRating() {
        ChatTelemetryService svc = new ChatTelemetryService(new TurnRegistry(), new LoggingChatTurnWriter());
        assertThrows(IllegalArgumentException.class,
                () -> svc.recordFeedback(new ChatFeedbackCommand("t1", "s1", "maybe", null)));
    }
}
