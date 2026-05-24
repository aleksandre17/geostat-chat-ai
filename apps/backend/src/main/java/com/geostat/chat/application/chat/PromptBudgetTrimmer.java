package com.geostat.chat.application.chat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;

/** Truncates system prompt assembly to configured character budget (H4). */
public final class PromptBudgetTrimmer {

    private PromptBudgetTrimmer() {}

    public static int effectiveRagBudget(int requestedRagChars, int systemPromptLength, int maxSystemPromptChars) {
        if (maxSystemPromptChars <= 0 || systemPromptLength <= maxSystemPromptChars) {
            return requestedRagChars;
        }
        int overflow = systemPromptLength - maxSystemPromptChars;
        return Math.max(1500, requestedRagChars - overflow - 500);
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
            int rowLen = chunk.text().length() + (chunk.sourceUrl() != null ? chunk.sourceUrl().length() : 0) + 8;
            if (used + rowLen > maxTotalChars) {
                break;
            }
            trimmed.add(chunk);
            used += rowLen;
        }
        return List.copyOf(trimmed);
    }
}
