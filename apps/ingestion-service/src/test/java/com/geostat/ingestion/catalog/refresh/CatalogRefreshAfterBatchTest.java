package com.geostat.ingestion.catalog.refresh;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.geostat.ingestion.catalog.AggregationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CatalogRefreshAfterBatchTest {

    @Mock
    private CatalogViewRefreshService catalogViewRefreshService;

    @Mock
    private ObjectProvider<CatalogViewRefreshService> catalogViewRefreshServiceProvider;

    private AggregationProperties aggregationProperties;

    @BeforeEach
    void setUp() {
        aggregationProperties = new AggregationProperties();
    }

    @Test
    void refreshIfConfiguredSkipsWhenAggregationDisabled() {
        aggregationProperties.setEnabled(false);
        aggregationProperties.setRefreshAfterEnrichmentBatch(true);
        CatalogRefreshAfterBatch hook = new CatalogRefreshAfterBatch(aggregationProperties, catalogViewRefreshServiceProvider);

        hook.refreshIfConfigured("authority-recompute");

        verify(catalogViewRefreshServiceProvider, never()).getIfAvailable();
        verifyNoInteractions(catalogViewRefreshService);
    }

    @Test
    void refreshIfConfiguredRunsWhenEnabled() {
        aggregationProperties.setEnabled(true);
        aggregationProperties.setRefreshAfterEnrichmentBatch(true);
        org.mockito.Mockito.when(catalogViewRefreshServiceProvider.getIfAvailable()).thenReturn(catalogViewRefreshService);
        CatalogRefreshAfterBatch hook = new CatalogRefreshAfterBatch(aggregationProperties, catalogViewRefreshServiceProvider);

        hook.refreshIfConfigured("topic-remine");

        verify(catalogViewRefreshService).refreshAll();
    }

    @Test
    void refreshIfConfiguredSkipsWhenBatchHookDisabled() {
        aggregationProperties.setEnabled(true);
        aggregationProperties.setRefreshAfterEnrichmentBatch(false);
        CatalogRefreshAfterBatch hook = new CatalogRefreshAfterBatch(aggregationProperties, catalogViewRefreshServiceProvider);

        hook.refreshIfConfigured("topic-remine");

        verify(catalogViewRefreshServiceProvider, never()).getIfAvailable();
    }
}
