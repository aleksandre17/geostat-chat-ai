package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("encoding_issue_rate")
public class EncodingIssueRateMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public EncodingIssueRateMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "encoding_issue_rate";
    }

    @Override
    public String description() {
        return "Share of parsed docs with encoding corruption flag";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT SUM(CASE WHEN encoding_issue THEN 1 ELSE 0 END)::float
                     / NULLIF(COUNT(*), 0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
