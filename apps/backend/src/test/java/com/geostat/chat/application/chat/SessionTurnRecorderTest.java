package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.geostat.chat.domain.catalog.Topic;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionTurnRecorderTest {

    private final SessionTurnRecorder recorder = new SessionTurnRecorder(3, 100);

    @Test
    void formatAssistantTurn_includesTopicsLinksAndRag() {
        String turn = recorder.formatAssistantTurn(
                "იხილეთ მონაცემები.",
                List.of("https://www.geostat.ge/ka/population"),
                List.of(Topic.POPULATION),
                List.of(new SessionTurnRecorder.RagExcerpt("https://www.geostat.ge/ka/pop", "passage text")));
        assertTrue(turn.contains("topics=POPULATION"));
        assertTrue(turn.contains("links=https://www.geostat.ge/ka/population"));
        assertTrue(turn.contains("rag=https://www.geostat.ge/ka/pop:passage text"));
    }

    @Test
    void excerptsFromChunks_limitsCountAndLength() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("1", "https://a", "x".repeat(200), 0.1),
                new RetrievedChunk("2", "https://b", "y", 0.2),
                new RetrievedChunk("3", "https://c", "z", 0.3),
                new RetrievedChunk("4", "https://d", "w", 0.4));
        List<SessionTurnRecorder.RagExcerpt> excerpts = recorder.excerptsFromChunks(chunks);
        assertEquals(3, excerpts.size());
        assertTrue(excerpts.get(0).excerpt().endsWith("…"));
    }
}
