package com.geostat.chat.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.catalog.Topic;
import org.junit.jupiter.api.Test;

class DerivedMinimalTopicCatalogTest {

    private final DerivedMinimalTopicCatalog catalog =
            new DerivedMinimalTopicCatalog(YamlPresentationStyleCatalog.fromClasspath());

    @Test
    void exposesGeneralTopicWithoutYamlRules() {
        assertThat(catalog.all()).hasSize(1);
        assertThat(catalog.get(Topic.GENERAL).rules()).isEmpty();
        assertThat(catalog.matchSpecificLinks("inflation")).isEmpty();
        assertThat(catalog.allPortals()).isEmpty();
    }

    @Test
    void stylesComeFromPresentationCatalog() {
        var style = catalog.get(Topic.PRICES).style();
        assertThat(style.icon()).isNotBlank();
        assertThat(style.bgColor()).startsWith("#");
    }
}
