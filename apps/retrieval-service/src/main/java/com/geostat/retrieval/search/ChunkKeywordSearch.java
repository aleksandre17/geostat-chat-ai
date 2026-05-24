package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** RAG-L06: Postgres full-text keyword search merged with vector hits when JDBC is configured. */
@Component
@ConditionalOnProperty(prefix = "geostat.retrieval.keyword", name = "enabled", havingValue = "true")
public class ChunkKeywordSearch {

    private static final String SQL = """
            SELECT c.id::text AS chunk_id, d.id::text AS document_id, d.canonical_url, c.text,
                   COALESCE(d.language, 'ka') AS language, COALESCE(d.title, '') AS page_title,
                   COALESCE(d.display_description, '') AS page_description
            FROM ingestion.chunk c
            JOIN ingestion.document d ON d.id = c.document_id
            WHERE (? IS NULL OR d.language = ?)
              AND to_tsvector('simple', c.text) @@ plainto_tsquery('simple', ?)
            ORDER BY ts_rank(to_tsvector('simple', c.text), plainto_tsquery('simple', ?)) DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public ChunkKeywordSearch(@Qualifier("retrievalKeywordJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RetrievedChunk> search(String query, String locale, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String loc = locale == null || locale.isBlank() ? null : locale.toLowerCase(Locale.ROOT);
        return jdbc.query(
                SQL,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getString("document_id"),
                        rs.getString("canonical_url"),
                        rs.getString("text"),
                        0.75 - rowNum * 0.01,
                        rs.getString("language"),
                        emptyToNull(rs.getString("page_title")),
                        null,
                        emptyToNull(rs.getString("page_description")),
                        null),
                loc,
                loc,
                query,
                query,
                limit);
    }

    public static List<RetrievedChunk> mergeVectorAndKeyword(
            List<RetrievedChunk> vectorHits, List<RetrievedChunk> keywordHits, int limit) {
        List<RetrievedChunk> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        appendUnique(merged, seen, keywordHits, limit);
        appendUnique(merged, seen, vectorHits, limit);
        return List.copyOf(merged);
    }

    private static void appendUnique(
            List<RetrievedChunk> out, Set<String> seen, List<RetrievedChunk> hits, int limit) {
        for (RetrievedChunk hit : hits) {
            if (out.size() >= limit) {
                return;
            }
            String key = hit.sourceUrl() != null ? hit.sourceUrl().toLowerCase(Locale.ROOT) : hit.documentId();
            if (seen.add(key)) {
                out.add(hit);
            }
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
