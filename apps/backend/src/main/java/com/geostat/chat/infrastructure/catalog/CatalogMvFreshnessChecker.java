package com.geostat.chat.infrastructure.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Checks materialized view freshness at startup when derived catalog is active.
 * Logs WARN if any catalog MV is older than the configured threshold.
 * Does NOT block startup — only warns.
 */
@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class CatalogMvFreshnessChecker {

    private static final Logger log = LoggerFactory.getLogger(CatalogMvFreshnessChecker.class);

    private final JdbcTemplate catalogJdbcTemplate;

    @Value("${geostat.chat.catalog.freshness-warn-minutes:60}")
    private int freshnessWarnMinutes;

    public CatalogMvFreshnessChecker(JdbcTemplate catalogJdbcTemplate) {
        this.catalogJdbcTemplate = catalogJdbcTemplate;
    }

    public void checkFreshness() {
        try {
            List<Map<String, Object>> rows = catalogJdbcTemplate.queryForList(
                    "SELECT view_name, refreshed_at FROM ingestion.catalog_view_refresh");
            Instant threshold = Instant.now().minus(freshnessWarnMinutes, ChronoUnit.MINUTES);
            for (Map<String, Object> row : rows) {
                Instant refreshedAt = toInstant(row.get("refreshed_at"));
                if (refreshedAt != null && refreshedAt.isBefore(threshold)) {
                    log.warn(
                            "[catalog-freshness] MV '{}' is stale: last refresh {} (threshold: {} min ago)",
                            row.get("view_name"),
                            refreshedAt,
                            freshnessWarnMinutes);
                }
            }
        } catch (Exception e) {
            log.warn("[catalog-freshness] Could not check MV freshness: {}", e.getMessage());
        }
    }

    private static Instant toInstant(Object refreshedAt) {
        if (refreshedAt == null) {
            return null;
        }
        if (refreshedAt instanceof Instant instant) {
            return instant;
        }
        if (refreshedAt instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (refreshedAt instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }
}
