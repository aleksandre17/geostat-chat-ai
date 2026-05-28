package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("skip_rate")
public class SkipRateMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public SkipRateMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "skip_rate";
    }

    @Override
    public String description() {
        return "Share of parsed docs classified as SKIP (portal landings, no statistical content)";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT SUM(CASE WHEN quality_score = 'skip'
                           THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
