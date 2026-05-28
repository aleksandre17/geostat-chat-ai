package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("summary_coverage")
public class SummaryCoverageMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public SummaryCoverageMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "summary_coverage";
    }

    @Override
    public String description() {
        return "Layer 2 — share of parsed docs with non-blank summary_ka or summary_en";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT
                  SUM(CASE WHEN COALESCE(summary_ka,'') <> '' OR COALESCE(summary_en,'') <> ''
                           THEN 1 ELSE 0 END)::float
                  / NULLIF(COUNT(*),0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
