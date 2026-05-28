package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("chunk_coverage")
public class ChunkCoverageMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public ChunkCoverageMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "chunk_coverage";
    }

    @Override
    public String description() {
        return "Share of parsed (non-skipped) docs that produced at least one chunk";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT c.document_id)::float
                     / NULLIF((SELECT COUNT(*) FROM ingestion.document
                               WHERE corpus_id=? AND fetch_status='parsed'),0)
                FROM ingestion.chunk c
                WHERE c.corpus_id = ?
                """,
                Double.class,
                corpusId,
                corpusId);
        return r != null ? r : 0.0;
    }
}
