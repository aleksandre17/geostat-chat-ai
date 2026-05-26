package com.geostat.ingestion.catalog.refresh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.aggregation", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.aggregation",
        name = "scheduler-enabled",
        havingValue = "true")
public class CatalogViewRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogViewRefreshJob.class);

    private final CatalogViewRefreshService catalogViewRefreshService;

    public CatalogViewRefreshJob(CatalogViewRefreshService catalogViewRefreshService) {
        this.catalogViewRefreshService = catalogViewRefreshService;
    }

    @Scheduled(cron = "${geostat.ingestion.aggregation.catalog-cron:0 0 3 * * *}")
    public void refreshAll() {
        try {
            catalogViewRefreshService.refreshAll();
        } catch (RuntimeException e) {
            log.warn("catalog view refresh failed: {}", e.getMessage());
        }
    }
}
