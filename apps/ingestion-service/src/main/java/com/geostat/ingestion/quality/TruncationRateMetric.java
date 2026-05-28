package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("truncation_rate")
public class TruncationRateMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public TruncationRateMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "truncation_rate";
    }

    @Override
    public String description() {
        return "Share of parsed docs with truncated_text validation violation";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT SUM(CASE WHEN 'truncated_text' = ANY(validation_violations)
                           THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
