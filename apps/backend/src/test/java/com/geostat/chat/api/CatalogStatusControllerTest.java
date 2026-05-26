package com.geostat.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import org.junit.jupiter.api.Test;

class CatalogStatusControllerTest {

    @Test
    void statusReportsYamlSourceWhenDerivedReaderAbsent() {
        CatalogProperties properties = new CatalogProperties();
        properties.setSource("yaml");
        properties.setJdbcUrl("");

        CatalogStatusController controller = new CatalogStatusController(properties, null);
        CatalogStatusController.CatalogStatus status = controller.status();

        assertThat(status.source()).isEqualTo("yaml");
        assertThat(status.derived()).isFalse();
        assertThat(status.derivedReaderActive()).isFalse();
        assertThat(status.jdbcConfigured()).isFalse();
    }

    @Test
    void statusReportsDerivedWhenReaderActive() {
        CatalogProperties properties = new CatalogProperties();
        properties.setSource("derived");
        properties.setJdbcUrl("jdbc:postgresql://localhost:5432/geostat");

        DerivedCatalogReader reader = mock(DerivedCatalogReader.class);
        CatalogStatusController controller = new CatalogStatusController(properties, reader);
        CatalogStatusController.CatalogStatus status = controller.status();

        assertThat(status.source()).isEqualTo("derived");
        assertThat(status.derived()).isTrue();
        assertThat(status.derivedReaderActive()).isTrue();
        assertThat(status.jdbcConfigured()).isTrue();
    }
}
