package com.geostat.ingestion.api;

import com.geostat.ingestion.catalog.refresh.CatalogViewRefreshService;
import com.geostat.ingestion.catalog.refresh.CatalogViewRefreshService.CatalogRefreshReport;
import com.geostat.ingestion.catalog.refresh.CatalogViewStatusService;
import com.geostat.ingestion.catalog.refresh.CatalogViewStatusService.CatalogStatusReport;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion")
public class CatalogController {

    private final Optional<CatalogViewRefreshService> catalogViewRefreshService;
    private final Optional<CatalogViewStatusService> catalogViewStatusService;

    public CatalogController(
            ObjectProvider<CatalogViewRefreshService> catalogViewRefreshService,
            ObjectProvider<CatalogViewStatusService> catalogViewStatusService) {
        this.catalogViewRefreshService = Optional.ofNullable(catalogViewRefreshService.getIfAvailable());
        this.catalogViewStatusService = Optional.ofNullable(catalogViewStatusService.getIfAvailable());
    }

    /** RAG-U02 — MV freshness (spec §8 mv-stale degraded mode telemetry). */
    @GetMapping("/catalog/status")
    public CatalogStatusResponse catalogStatus() {
        CatalogViewStatusService service = catalogViewStatusService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Aggregation disabled — set geostat.ingestion.aggregation.enabled=true (RAG-U02)"));
        CatalogStatusReport report = service.status();
        List<ViewStatusResponse> views = report.views().stream()
                .map(view -> new ViewStatusResponse(
                        view.viewName(),
                        view.lastRefreshedAt() != null ? view.lastRefreshedAt().toString() : null,
                        view.ageHours(),
                        view.stale()))
                .toList();
        return new CatalogStatusResponse(
                report.checkedAt().toString(),
                report.staleThresholdHours(),
                report.anyStale(),
                views);
    }

    /** RAG-U02 — refresh Layer 3 catalog materialized views (idempotent). */
    @PostMapping("/catalog:refresh")
    public CatalogRefreshResponse refreshCatalogViews() {
        CatalogViewRefreshService service = catalogViewRefreshService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Aggregation disabled — set geostat.ingestion.aggregation.enabled=true (RAG-U02)"));
        CatalogRefreshReport report = service.refreshAll();
        return new CatalogRefreshResponse(
                "accepted",
                report.startedAt().toString(),
                report.finishedAt().toString(),
                report.viewsRefreshed());
    }

    public record CatalogRefreshResponse(
            String status, String startedAt, String finishedAt, List<String> viewsRefreshed) {}

    public record CatalogStatusResponse(
            String checkedAt, long staleThresholdHours, boolean anyStale, List<ViewStatusResponse> views) {}

    public record ViewStatusResponse(String viewName, String lastRefreshedAt, long ageHours, boolean stale) {}
}
