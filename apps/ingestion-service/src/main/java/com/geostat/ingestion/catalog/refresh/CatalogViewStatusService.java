package com.geostat.ingestion.catalog.refresh;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Spec §8 — MV freshness for ops (`GET /catalog/status`, derivation readiness). */
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.aggregation", name = "enabled", havingValue = "true")
public class CatalogViewStatusService {

    static final Duration STALE_AFTER = Duration.ofHours(25);

    private final JdbcTemplate jdbcTemplate;

    public CatalogViewStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CatalogStatusReport status() {
        Instant now = Instant.now();
        List<ViewStatus> views = new ArrayList<>(CatalogMaterializedViews.ALL.size());
        boolean anyStale = false;
        for (String viewName : CatalogMaterializedViews.ALL) {
            Instant refreshedAt = loadLastRefresh(viewName);
            long ageHours = ageHours(refreshedAt, now);
            boolean stale = refreshedAt == null || ageHours > STALE_AFTER.toHours();
            anyStale |= stale;
            views.add(new ViewStatus(viewName, refreshedAt, ageHours, stale));
        }
        return new CatalogStatusReport(now, STALE_AFTER.toHours(), anyStale, views);
    }

    private Instant loadLastRefresh(String viewName) {
        List<Instant> rows = jdbcTemplate.query(
                """
                SELECT refreshed_at
                FROM ingestion.catalog_view_refresh
                WHERE view_name = ?
                """,
                (rs, rowNum) -> rs.getTimestamp("refreshed_at").toInstant(),
                viewName);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static long ageHours(Instant refreshedAt, Instant now) {
        if (refreshedAt == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(refreshedAt, now).toHours();
    }

    public record CatalogStatusReport(
            Instant checkedAt, long staleThresholdHours, boolean anyStale, List<ViewStatus> views) {}

    public record ViewStatus(String viewName, Instant lastRefreshedAt, long ageHours, boolean stale) {}
}
