package com.geostat.ingestion.quality;

import com.geostat.platform.quality.QualityMetric;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("boilerplate_ratio")
public class BoilerplateRatioMetric implements QualityMetric {

    private final JdbcTemplate jdbcTemplate;

    public BoilerplateRatioMetric(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return "boilerplate_ratio";
    }

    @Override
    public String description() {
        return "Share of parsed docs whose content_text contains an accessibility/footer marker";
    }

    @Override
    public double compute(UUID corpusId) {
        Double r = jdbcTemplate.queryForObject(
                """
                SELECT
                  SUM(CASE WHEN content_text ILIKE '%adapted version of the website%'
                            OR content_text ILIKE '%ვებგვერდის ადაპტ%' THEN 1 ELSE 0 END)::float
                  / NULLIF(COUNT(*),0)
                FROM ingestion.document
                WHERE corpus_id = ? AND fetch_status = 'parsed'
                """,
                Double.class,
                corpusId);
        return r != null ? r : 0.0;
    }
}
