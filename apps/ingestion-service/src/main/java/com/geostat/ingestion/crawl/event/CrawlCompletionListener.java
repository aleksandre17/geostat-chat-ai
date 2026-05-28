package com.geostat.ingestion.crawl.event;

import com.geostat.ingestion.catalog.refresh.CatalogViewRefreshService;
import com.geostat.ingestion.enrichment.authority.AuthorityRecomputeService;
import com.geostat.ingestion.enrichment.runner.EnrichmentBackfillAlreadyRunningException;
import com.geostat.ingestion.enrichment.runner.EnrichmentBackfillService;
import com.geostat.ingestion.vector.VectorCleanupJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Post-crawl pipeline: enrichment backfill → authority recompute → MV refresh → vector cleanup.
 * Runs asynchronously after each successful crawl completion.
 */
@Component
@Profile("db")
public class CrawlCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(CrawlCompletionListener.class);

    private final EnrichmentBackfillService enrichmentBackfillService;
    private final AuthorityRecomputeService authorityRecomputeService;
    private final CatalogViewRefreshService catalogViewRefreshService;
    private final VectorCleanupJob vectorCleanupJob;

    public CrawlCompletionListener(
            EnrichmentBackfillService enrichmentBackfillService,
            AuthorityRecomputeService authorityRecomputeService,
            CatalogViewRefreshService catalogViewRefreshService,
            VectorCleanupJob vectorCleanupJob) {
        this.enrichmentBackfillService = enrichmentBackfillService;
        this.authorityRecomputeService = authorityRecomputeService;
        this.catalogViewRefreshService = catalogViewRefreshService;
        this.vectorCleanupJob          = vectorCleanupJob;
    }

    @Async
    @EventListener
    public void onCrawlCompleted(CrawlCompletionEvent event) {
        log.info("[post-crawl] corpus={} run={} — starting post-crawl pipeline",
                event.corpusName(), event.runId());

        // 1. Enrichment backfill — enrich all un-enriched documents
        try {
            int queued = enrichmentBackfillService.queueBackfill(event.corpusName(), Integer.MAX_VALUE, true);
            log.info("[post-crawl] corpus={} — enrichment backfill queued {} documents",
                    event.corpusName(), queued);
        } catch (EnrichmentBackfillAlreadyRunningException e) {
            log.warn("[post-crawl] corpus={} — enrichment backfill already running, skipping",
                    event.corpusName());
        } catch (Exception e) {
            log.error("[post-crawl] corpus={} — enrichment backfill failed", event.corpusName(), e);
        }

        // 2. Authority score recompute
        try {
            authorityRecomputeService.recomputeForCorpus(event.corpusId());
            log.info("[post-crawl] corpus={} — authority recomputed", event.corpusName());
        } catch (Exception e) {
            log.error("[post-crawl] corpus={} — authority recompute failed", event.corpusName(), e);
        }

        // 3. Materialized view refresh — must happen BEFORE vector cleanup so MV is up-to-date
        try {
            catalogViewRefreshService.refreshAll();
            log.info("[post-crawl] corpus={} — catalog MVs refreshed", event.corpusName());
        } catch (Exception e) {
            log.error("[post-crawl] corpus={} — MV refresh failed", event.corpusName(), e);
        }

        // 4. Vector cleanup — remove orphan Qdrant vectors (excluded from MV after quality gate)
        try {
            VectorCleanupJob.CleanupResult cleanup = vectorCleanupJob.cleanOrphanVectors(event.corpusId());
            log.info("[post-crawl] corpus={} — vector cleanup done, deleted={}",
                    event.corpusName(), cleanup.deletedCount());
        } catch (Exception e) {
            log.error("[post-crawl] corpus={} — vector cleanup failed", event.corpusName(), e);
        }

        log.info("[post-crawl] corpus={} run={} — post-crawl pipeline complete",
                event.corpusName(), event.runId());
    }
}
