package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.SourceUrlNormalizer;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Locale;

/** Verifies AI explanations cite indexed corpus passages (P6 / R-03). */
public final class ExplanationGroundingVerifier {

    static final int MIN_PHRASE_CHARS = 20;
    static final int PASSAGE_WINDOW = 180;

    private ExplanationGroundingVerifier() {}

    public static boolean isGrounded(
            List<LinkedExplanation> items, List<RetrievedChunk> ragChunks, String intro) {
        if (items != null && items.stream().anyMatch(ExplanationGroundingVerifier::hasRagLink)) {
            return true;
        }
        if (ragChunks == null || ragChunks.isEmpty()) {
            return false;
        }
        if (intro != null && citesCorpus(intro, ragChunks, null)) {
            return true;
        }
        if (items == null) {
            return false;
        }
        return items.stream()
                .anyMatch(item -> item.explanation() != null
                        && !item.explanation().isBlank()
                        && citesCorpus(item.explanation(), ragChunks, item.link()));
    }

    public static boolean explanationCitesChunk(String explanation, String url, List<RetrievedChunk> chunks) {
        if (explanation == null || explanation.isBlank() || chunks == null) {
            return false;
        }
        String key = url != null ? SourceUrlNormalizer.normalize(url) : "";
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null || chunk.text() == null || chunk.text().isBlank()) {
                continue;
            }
            if (!key.isEmpty() && chunk.sourceUrl() != null
                    && !key.equals(SourceUrlNormalizer.normalize(chunk.sourceUrl()))) {
                continue;
            }
            if (containsPassagePhrase(normalize(explanation), normalize(chunk.text()))) {
                return true;
            }
        }
        return false;
    }

    static boolean citesCorpus(String text, List<RetrievedChunk> chunks, LinkCard link) {
        String url = link != null ? link.url() : null;
        if (url != null && !url.isBlank()) {
            if (explanationCitesChunk(text, url, chunks)) {
                return true;
            }
        }
        return explanationCitesChunk(text, null, chunks);
    }

    static boolean containsPassagePhrase(String explanation, String passage) {
        if (explanation.isBlank() || passage.isBlank()) {
            return false;
        }
        String window = passage.length() > PASSAGE_WINDOW ? passage.substring(0, PASSAGE_WINDOW) : passage;
        int maxLen = Math.min(40, window.length());
        for (int len = maxLen; len >= MIN_PHRASE_CHARS; len--) {
            for (int i = 0; i <= window.length() - len; i++) {
                String slice = window.substring(i, i + len).strip();
                if (slice.length() >= MIN_PHRASE_CHARS && explanation.contains(slice)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static boolean hasRagLink(LinkedExplanation item) {
        return item.link() != null && "rag".equals(item.link().sourceType());
    }
}
