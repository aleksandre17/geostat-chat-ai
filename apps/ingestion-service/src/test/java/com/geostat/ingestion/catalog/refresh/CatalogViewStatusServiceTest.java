package com.geostat.ingestion.catalog.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CatalogViewStatusServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CatalogViewStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService = new CatalogViewStatusService(jdbcTemplate);
    }

    @Test
    void marksViewStaleWhenNeverRefreshed() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object.class)))
                .thenReturn(List.of());

        CatalogViewStatusService.CatalogStatusReport report = statusService.status();

        assertThat(report.anyStale()).isTrue();
        assertThat(report.views()).hasSize(CatalogMaterializedViews.ALL.size());
        assertThat(report.views()).allMatch(CatalogViewStatusService.ViewStatus::stale);
    }

    @Test
    void freshWhenRefreshedRecently() {
        Instant recent = Instant.now().minusSeconds(3600);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(CatalogMaterializedViews.PORTAL_LINK)))
                .thenReturn(List.of(recent));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(CatalogMaterializedViews.SPECIFIC_LINK)))
                .thenReturn(List.of(recent));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(CatalogMaterializedViews.TOPIC_KEYWORDS)))
                .thenReturn(List.of(recent));

        CatalogViewStatusService.CatalogStatusReport report = statusService.status();

        assertThat(report.anyStale()).isFalse();
    }
}
