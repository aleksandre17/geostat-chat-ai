package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PromptBudgetTrimmerTest {

    @Test
    void effectiveRagBudget_reducesWhenPromptTooLong() {
        int adjusted = PromptBudgetTrimmer.effectiveRagBudget(12000, 30000, 28000);
        assertTrue(adjusted < 12000);
        assertTrue(adjusted >= 1500);
    }

    @Test
    void trimChunks_respectsCharLimit() {
        var chunks = java.util.List.of(
                new com.geostat.platform.contracts.retrieval.RetrievedChunk("1", "https://a", "a".repeat(5000), 0.1),
                new com.geostat.platform.contracts.retrieval.RetrievedChunk("2", "https://b", "b".repeat(5000), 0.2));
        assertEquals(1, PromptBudgetTrimmer.trimChunks(chunks, 6000).size());
    }
}
