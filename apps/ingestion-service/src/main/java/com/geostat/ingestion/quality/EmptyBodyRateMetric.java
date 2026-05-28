package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("empty_body_rate")
public class EmptyBodyRateMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public EmptyBodyRateMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "empty_body_rate";
    }

    @Override
    public String description() {
        return "Share of parsed docs with content_text shorter than 30 chars";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT
                  SUM(CASE WHEN COALESCE(length(content_text),0) < 30 THEN 1 ELSE 0 END)::float
                  / NULLIF(COUNT(*),0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
