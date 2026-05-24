package com.geostat.ingestion.quality.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class CorpusQualityReader {

    private static final String DOCUMENT_METRICS_SQL =
            """
            SELECT
              COUNT(d.id) AS total_documents,
              COUNT(d.id) FILTER (WHERE d.fetch_status = 'parsed') AS parsed_documents,
              COUNT(d.id) FILTER (WHERE d.fetch_status = 'failed') AS failed_documents,
              COUNT(d.id) FILTER (
                WHERE d.fetch_status = 'parsed'
                  AND (
                    d.content_text IS NULL
                    OR LENGTH(TRIM(d.content_text)) < ?
                  )
              ) AS empty_body_documents
            FROM ingestion.document d
            INNER JOIN ingestion.corpus c ON c.id = d.corpus_id
            WHERE c.name = ?
            """;

    private static final String PIPELINE_METRICS_SQL =
            """
            SELECT
              COALESCE((
                SELECT COUNT(*)
                FROM ingestion.chunk ch
                INNER JOIN ingestion.corpus c ON c.id = ch.corpus_id
                WHERE c.name = ?
              ), 0) AS total_chunks,
              COALESCE((
                SELECT COUNT(DISTINCT ch.document_id)
                FROM ingestion.chunk ch
                INNER JOIN ingestion.corpus c ON c.id = ch.corpus_id
                WHERE c.name = ?
              ), 0) AS documents_with_chunks,
              COALESCE((
                SELECT COUNT(*)
                FROM ingestion.vector_index vi
                INNER JOIN ingestion.chunk ch ON ch.id = vi.chunk_id
                INNER JOIN ingestion.corpus c ON c.id = ch.corpus_id
                WHERE c.name = ?
              ), 0) AS indexed_chunks
            """;

    private static final String SAMPLE_EMPTY_URLS_SQL =
            """
            SELECT d.canonical_url
            FROM ingestion.document d
            INNER JOIN ingestion.corpus c ON c.id = d.corpus_id
            WHERE c.name = ?
              AND d.fetch_status = 'parsed'
              AND (
                d.content_text IS NULL
                OR LENGTH(TRIM(d.content_text)) < ?
              )
            ORDER BY LENGTH(TRIM(COALESCE(d.content_text, ''))) ASC, d.canonical_url ASC
            LIMIT ?
            """;

    private static final String LATEST_CRAWL_RUN_SQL =
            """
            SELECT cr.status, cr.finished_at, cr.stats
            FROM ingestion.crawl_run cr
            INNER JOIN ingestion.corpus c ON c.id = cr.corpus_id
            WHERE c.name = ?
            ORDER BY cr.created_at DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public CorpusQualityReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean corpusExists(String corpusName) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ingestion.corpus WHERE name = ?",
                        Integer.class,
                        corpusName);
        return count != null && count > 0;
    }

    public CorpusDocumentMetrics loadDocumentMetrics(String corpusName, int minContentChars) {
        return jdbcTemplate.queryForObject(
                DOCUMENT_METRICS_SQL,
                (rs, rowNum) ->
                        new CorpusDocumentMetrics(
                                rs.getLong("total_documents"),
                                rs.getLong("parsed_documents"),
                                rs.getLong("failed_documents"),
                                rs.getLong("empty_body_documents")),
                minContentChars,
                corpusName);
    }

    public CorpusPipelineMetrics loadPipelineMetrics(String corpusName) {
        return jdbcTemplate.queryForObject(
                PIPELINE_METRICS_SQL,
                (rs, rowNum) ->
                        new CorpusPipelineMetrics(
                                rs.getLong("total_chunks"),
                                rs.getLong("documents_with_chunks"),
                                rs.getLong("indexed_chunks")),
                corpusName,
                corpusName,
                corpusName);
    }

    public List<String> sampleEmptyBodyUrls(String corpusName, int minContentChars, int limit) {
        return jdbcTemplate.query(
                SAMPLE_EMPTY_URLS_SQL,
                (rs, rowNum) -> rs.getString("canonical_url"),
                corpusName,
                minContentChars,
                limit);
    }

    public Optional<LatestCrawlRunSnapshot> loadLatestCrawlRun(String corpusName) {
        List<LatestCrawlRunSnapshot> rows =
                jdbcTemplate.query(LATEST_CRAWL_RUN_SQL, new CrawlRunRowMapper(), corpusName);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static final class CrawlRunRowMapper implements RowMapper<LatestCrawlRunSnapshot> {

        @Override
        public LatestCrawlRunSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instant finishedAt =
                    rs.getTimestamp("finished_at") == null
                            ? null
                            : rs.getTimestamp("finished_at").toInstant();
            Map<String, Object> stats = parseStats(rs.getString("stats"));
            return new LatestCrawlRunSnapshot(rs.getString("status"), finishedAt, stats);
        }

        private Map<String, Object> parseStats(String json) {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            try {
                return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Map.of("parseError", e.getMessage());
            }
        }
    }
}
