package com.geostat.chat.api;

import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ops visibility for catalog cutover (RAG-U02 / P1-15). */
@RestController
@RequestMapping("/api/v1/chat/catalog")
public class CatalogStatusController {

    private final CatalogProperties catalogProperties;
    private final DerivedCatalogReader derivedCatalogReader;

    public CatalogStatusController(
            CatalogProperties catalogProperties,
            @Autowired(required = false) DerivedCatalogReader derivedCatalogReader) {
        this.catalogProperties = catalogProperties;
        this.derivedCatalogReader = derivedCatalogReader;
    }

    @GetMapping("/status")
    public CatalogStatus status() {
        return new CatalogStatus(
                catalogProperties.source(),
                catalogProperties.isDerived(),
                derivedCatalogReader != null,
                catalogProperties.jdbcUrl() != null && !catalogProperties.jdbcUrl().isBlank());
    }

    public record CatalogStatus(String source, boolean derived, boolean derivedReaderActive, boolean jdbcConfigured) {}
}
