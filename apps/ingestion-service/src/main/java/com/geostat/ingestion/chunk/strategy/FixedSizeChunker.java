package com.geostat.ingestion.chunk.strategy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Splits cleaned page text into overlapping fixed-size segments (baseline RAG strategy).
 */
@Component
public class FixedSizeChunker {

    public static final String STRATEGY_ID = "fixed-size-v1";
    static final int DEFAULT_MAX_CHARS = 800;
    static final int DEFAULT_OVERLAP_CHARS = 120;

    public List<TextChunk> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    List<TextChunk> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank() || maxChars <= 0) {
            return List.of();
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return List.of(new TextChunk(0, normalized));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int sequence = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxChars, normalized.length());
            if (end < normalized.length()) {
                int wordBreak = normalized.lastIndexOf(' ', end);
                if (wordBreak > start + maxChars / 4) {
                    end = wordBreak;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new TextChunk(sequence++, piece));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
