package com.geostat.ingestion.catalog.refresh;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.aggregation", name = "enabled", havingValue = "true")
public class CatalogViewRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CatalogViewRefreshService.class);

    private final JdbcTemplate jdbcTemplate;

    public CatalogViewRefreshService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CatalogRefreshReport refreshAll() {
        Instant started = Instant.now();
        List<String> refreshed = new ArrayList<>(CatalogMaterializedViews.ALL.size());
        Instant finishedAt = started;
        for (String viewName : CatalogMaterializedViews.ALL) {
            jdbcTemplate.execute(CatalogMaterializedViews.refreshConcurrentlySql(viewName));
            finishedAt = Instant.now();
            recordRefresh(viewName, finishedAt);
            refreshed.add(viewName);
            log.debug("refreshed materialized view {}", viewName);
        }
        log.info("catalog views refreshed — views={}, durationMs={}", refreshed.size(), durationMs(started, finishedAt));
        return new CatalogRefreshReport(started, finishedAt, refreshed);
    }

    private void recordRefresh(String viewName, Instant refreshedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ingestion.catalog_view_refresh (view_name, refreshed_at)
                VALUES (?, ?)
                ON CONFLICT (view_name) DO UPDATE SET refreshed_at = EXCLUDED.refreshed_at
                """,
                viewName,
                java.sql.Timestamp.from(refreshedAt));
    }

    private static long durationMs(Instant started, Instant finished) {
        return finished.toEpochMilli() - started.toEpochMilli();
    }

    public record CatalogRefreshReport(Instant startedAt, Instant finishedAt, List<String> viewsRefreshed) {}
}
