package com.geostat.ingestion.catalog.refresh;

import com.geostat.ingestion.catalog.AggregationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class CatalogRefreshAfterBatch {

    private static final Logger log = LoggerFactory.getLogger(CatalogRefreshAfterBatch.class);

    private final AggregationProperties aggregationProperties;
    private final ObjectProvider<CatalogViewRefreshService> catalogViewRefreshService;

    public CatalogRefreshAfterBatch(
            AggregationProperties aggregationProperties,
            ObjectProvider<CatalogViewRefreshService> catalogViewRefreshService) {
        this.aggregationProperties = aggregationProperties;
        this.catalogViewRefreshService = catalogViewRefreshService;
    }

    public void refreshIfConfigured(String trigger) {
        if (!aggregationProperties.isEnabled() || !aggregationProperties.isRefreshAfterEnrichmentBatch()) {
            return;
        }
        CatalogViewRefreshService service = catalogViewRefreshService.getIfAvailable();
        if (service == null) {
            log.debug("catalog refresh skipped after {} — service unavailable", trigger);
            return;
        }
        try {
            service.refreshAll();
        } catch (RuntimeException e) {
            log.warn("catalog refresh after {} failed: {}", trigger, e.getMessage());
        }
    }
}
