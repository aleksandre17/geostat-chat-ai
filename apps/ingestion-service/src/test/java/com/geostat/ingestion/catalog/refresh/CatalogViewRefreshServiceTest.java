package com.geostat.ingestion.catalog.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CatalogViewRefreshServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CatalogViewRefreshService refreshService;

    @BeforeEach
    void setUp() {
        refreshService = new CatalogViewRefreshService(jdbcTemplate);
    }

    @Test
    void refreshAllRefreshesViewsConcurrentlyInOrder() {
        CatalogViewRefreshService.CatalogRefreshReport report = refreshService.refreshAll();

        InOrder order = inOrder(jdbcTemplate);
        for (String viewName : CatalogMaterializedViews.ALL) {
            order.verify(jdbcTemplate).execute(CatalogMaterializedViews.refreshConcurrentlySql(viewName));
            order.verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(viewName), org.mockito.ArgumentMatchers.any());
        }
        assertThat(report.viewsRefreshed()).isEqualTo(CatalogMaterializedViews.ALL);
    }

    @Test
    void refreshConcurrentlySqlUsesConcurrentRefreshKeyword() {
        assertThat(CatalogMaterializedViews.refreshConcurrentlySql(CatalogMaterializedViews.PORTAL_LINK))
                .isEqualTo("REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_portal_link");
    }
}
