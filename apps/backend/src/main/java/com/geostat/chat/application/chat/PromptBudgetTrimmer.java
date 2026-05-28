package com.geostat.chat.application.chat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;

/** Truncates system prompt assembly to configured character budget (H4). */
public final class PromptBudgetTrimmer {

    private PromptBudgetTrimmer() {}

    /**
     * Returns the effective RAG budget after accounting for system prompt overflow.
     *
     * @param requestedRagChars  desired RAG budget
     * @param systemPromptLength assembled prompt length before trim
     * @param maxSystemPromptChars configurable hard cap on the system prompt
     * @param minRagChars        minimum guaranteed RAG chars (never go below this)
     * @param safetyMargin       extra buffer subtracted from the remaining budget
     */
    public static int effectiveRagBudget(
            int requestedRagChars,
            int systemPromptLength,
            int maxSystemPromptChars,
            int minRagChars,
            int safetyMargin) {
        if (maxSystemPromptChars <= 0 || systemPromptLength <= maxSystemPromptChars) {
            return requestedRagChars;
        }
        int overflow = systemPromptLength - maxSystemPromptChars;
        return Math.max(minRagChars, requestedRagChars - overflow - safetyMargin);
    }

    public static List<RetrievedChunk> trimChunks(List<RetrievedChunk> chunks, int maxTotalChars) {
        if (chunks == null || chunks.isEmpty() || maxTotalChars <= 0) {
            return chunks != null ? chunks : List.of();
        }
        List<RetrievedChunk> trimmed = new ArrayList<>();
        int used = 0;
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null || chunk.text() == null) {
                continue;
            }
            int rowLen = chunk.text().length()
                    + (chunk.sourceUrl() != null ? chunk.sourceUrl().length() : 0)
                    + 8;
            if (used + rowLen > maxTotalChars) {
                break;
            }
            trimmed.add(chunk);
            used += rowLen;
        }
        return List.copyOf(trimmed);
    }
}
