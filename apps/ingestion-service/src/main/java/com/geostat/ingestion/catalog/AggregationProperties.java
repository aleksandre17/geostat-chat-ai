package com.geostat.ingestion.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion.aggregation")
public class AggregationProperties {

    /** Master switch for Layer 3 catalog view refresh (RAG-U02). Default off until ops enables. */
    private boolean enabled = false;

    private boolean schedulerEnabled = false;

    private String catalogCron = "0 0 3 * * *";

    /** Refresh catalog views after authority/topic batch jobs when aggregation is enabled. */
    private boolean refreshAfterEnrichmentBatch = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public String catalogCron() {
        return catalogCron;
    }

    public void setCatalogCron(String catalogCron) {
        this.catalogCron = catalogCron;
    }

    public boolean isRefreshAfterEnrichmentBatch() {
        return refreshAfterEnrichmentBatch;
    }

    public void setRefreshAfterEnrichmentBatch(boolean refreshAfterEnrichmentBatch) {
        this.refreshAfterEnrichmentBatch = refreshAfterEnrichmentBatch;
    }
}
