package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Component;

/** RAG-L06: Postgres full-text keyword search merged with vector hits when JDBC is configured. */
@Component
@ConditionalOnProperty(prefix = "geostat.retrieval.keyword", name = "enabled", havingValue = "true")
public class ChunkKeywordSearch {

    private static final String SQL = """
            SELECT c.id::text AS chunk_id, d.id::text AS document_id, d.canonical_url, c.text,
                   COALESCE(d.language, 'ka') AS language, COALESCE(d.title, '') AS page_title,
                   COALESCE(d.display_description, '') AS page_description,
                   c.sequence_no
            FROM ingestion.chunk c
            JOIN ingestion.document d ON d.id = c.document_id
            WHERE d.fetch_status = 'parsed'
              AND COALESCE(d.page_kind, 'unknown') <> 'navigation'
              AND NOT EXISTS (
                  SELECT 1 FROM ingestion.curation_override co
                  WHERE co.url_hash = d.url_hash AND co.action = 'exclude'
                    AND (co.expires_at IS NULL OR co.expires_at > now())
              )
              AND (? IS NULL OR d.language = ?)
              AND (
                  to_tsvector('simple',
                      coalesce(c.text, '') || ' ' || coalesce(d.title, '') || ' '
                          || replace(coalesce(d.canonical_url, ''), '/', ' '))
                  @@ plainto_tsquery('simple', ?)
                  OR coalesce(d.title, '') ILIKE ANY (?)
                  OR coalesce(d.canonical_url, '') ILIKE ANY (?)
                  OR coalesce(c.text, '') ILIKE ANY (?)
              )
            ORDER BY
              CASE WHEN c.sequence_no = 0 THEN 0 ELSE 1 END,
              ts_rank(
                  to_tsvector('simple',
                      coalesce(c.text, '') || ' ' || coalesce(d.title, '') || ' '
                          || replace(coalesce(d.canonical_url, ''), '/', ' ')),
                  plainto_tsquery('simple', ?)) DESC
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
        String trimmed = query.strip();
        String[] patterns = ilikePatterns(trimmed).toArray(String[]::new);
        return jdbc.query(
                SQL,
                (PreparedStatementSetter)
                        ps -> {
                            ps.setString(1, loc);
                            ps.setString(2, loc);
                            ps.setString(3, trimmed);
                            Connection conn = ps.getConnection();
                            ps.setArray(4, conn.createArrayOf("text", patterns));
                            ps.setArray(5, conn.createArrayOf("text", patterns));
                            ps.setArray(6, conn.createArrayOf("text", patterns));
                            ps.setString(7, trimmed);
                            ps.setInt(8, limit);
                        },
                (rs, rowNum) ->
                        new RetrievedChunk(
                                rs.getString("document_id"),
                                rs.getString("canonical_url"),
                                rs.getString("text"),
                                0.85 - rowNum * 0.01,
                                rs.getString("language"),
                                emptyToNull(rs.getString("page_title")),
                                null,
                                emptyToNull(rs.getString("page_description")),
                                null));
    }

    static List<String> ilikePatterns(String query) {
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        String trimmed = query.strip();
        if (!trimmed.isEmpty()) {
            patterns.add("%" + trimmed + "%");
        }
        for (String part : trimmed.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (part.length() >= 5) {
                patterns.add("%" + part + "%");
                patterns.add("%" + part.substring(0, part.length() - 1) + "%");
            } else if (part.length() >= 4) {
                patterns.add("%" + part + "%");
            }
        }
        return patterns.stream().limit(8).toList();
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
