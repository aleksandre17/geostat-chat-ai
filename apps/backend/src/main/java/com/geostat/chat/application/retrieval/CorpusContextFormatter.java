package com.geostat.chat.application.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.stream.Collectors;

/** Formats ingested corpus passages for AI prompts (single source — ingestion → retrieval). */
public final class CorpusContextFormatter {

    private CorpusContextFormatter() {}

    public static String formatPassages(List<RetrievedChunk> chunks, boolean isGeorgian) {
        return formatPassages(chunks, isGeorgian, Integer.MAX_VALUE);
    }

    public static String formatPassages(List<RetrievedChunk> chunks, boolean isGeorgian, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return isGeorgian
                    ? "(საიტის ინდექსიდან ჩანაწერები ვერ მოიძებნა — reindex/crawl საჭიროა)"
                    : "(no indexed site passages found — reindex/crawl required)";
        }
        String header = isGeorgian
                ? "\n\n## ინფორმაცია საიტის ინდექსიდან (crawler4j + Jsoup pipeline)\n"
                : "\n\n## Indexed site content (crawler4j + Jsoup pipeline)\n";
        StringBuilder body = new StringBuilder();
        int used = 0;
        for (RetrievedChunk chunk : chunks) {
            String row = "- " + chunk.sourceUrl() + "\n  " + chunk.text() + "\n";
            if (used + row.length() > maxChars) {
                break;
            }
            body.append(row);
            used += row.length();
        }
        return header + body;
    }
}
