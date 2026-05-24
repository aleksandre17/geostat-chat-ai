package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.Topic;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Formats assistant turn text stored in session history for multi-turn Gemini context. */
public final class SessionTurnRecorder {

    private static final String META_PREFIX = "\n\n[turn-meta: ";
    private static final String META_SUFFIX = "]";
    static final int MAX_RAG_EXCERPTS = 3;
    static final int EXCERPT_CHARS = 100;

    public record RagExcerpt(String url, String excerpt) {}

    private SessionTurnRecorder() {}

    public static String formatAssistantTurn(
            String intro, List<String> urls, List<Topic> topics, List<RagExcerpt> ragExcerpts) {
        StringBuilder sb = new StringBuilder(intro != null ? intro.strip() : "");
        if ((urls != null && !urls.isEmpty())
                || (topics != null && !topics.isEmpty())
                || (ragExcerpts != null && !ragExcerpts.isEmpty())) {
            sb.append(META_PREFIX);
            boolean needSep = false;
            if (topics != null && !topics.isEmpty()) {
                sb.append("topics=")
                        .append(topics.stream().map(Topic::name).collect(Collectors.joining(",")));
                needSep = true;
            }
            if (urls != null && !urls.isEmpty()) {
                if (needSep) {
                    sb.append("; ");
                }
                sb.append("links=").append(String.join(",", urls));
                needSep = true;
            }
            if (ragExcerpts != null && !ragExcerpts.isEmpty()) {
                if (needSep) {
                    sb.append("; ");
                }
                sb.append("rag=")
                        .append(ragExcerpts.stream()
                                .map(r -> r.url() + ":" + r.excerpt())
                                .collect(Collectors.joining("|")));
            }
            sb.append(META_SUFFIX);
        }
        return sb.toString();
    }

    public static List<RagExcerpt> excerptsFromChunks(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<RagExcerpt> excerpts = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            if (excerpts.size() >= MAX_RAG_EXCERPTS) {
                break;
            }
            if (chunk == null || chunk.sourceUrl() == null || chunk.sourceUrl().isBlank()) {
                continue;
            }
            String text = chunk.text() != null ? chunk.text().strip() : "";
            if (text.length() > EXCERPT_CHARS) {
                text = text.substring(0, EXCERPT_CHARS).strip() + "…";
            }
            excerpts.add(new RagExcerpt(chunk.sourceUrl().strip(), text));
        }
        return List.copyOf(excerpts);
    }
}
