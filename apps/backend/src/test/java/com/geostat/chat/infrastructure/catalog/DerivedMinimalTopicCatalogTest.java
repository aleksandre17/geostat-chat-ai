package com.geostat.chat.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.catalog.TopicRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DerivedMinimalTopicCatalogTest {

    private final DerivedMinimalTopicCatalog emptyCatalog =
            new DerivedMinimalTopicCatalog(YamlPresentationStyleCatalog.fromClasspath(), Map.of());

    @Test
    void exposesGeneralTopicWithoutDbRules() {
        assertThat(emptyCatalog.all()).hasSize(1);
        assertThat(emptyCatalog.get(Topic.GENERAL).rules()).isEmpty();
        assertThat(emptyCatalog.matchSpecificLinks("inflation")).isEmpty();
        assertThat(emptyCatalog.allPortals()).isEmpty();
    }

    @Test
    void stylesComeFromPresentationCatalog() {
        var style = emptyCatalog.get(Topic.PRICES).style();
        assertThat(style.icon()).isNotBlank();
        assertThat(style.bgColor()).startsWith("#");
    }

    @Test
    void loadedDefinitionsExposeClusterKeywordsAsRules() {
        TopicDefinition prices = TopicDefinition.of(
                Topic.PRICES,
                List.of(TopicRule.simple(20, List.of("ინფლაცია", "inflation"))),
                List.of(),
                null,
                null,
                null,
                new TopicDefinition.TopicStyle("news", "#10B981", "#D1FAE5"),
                0);
        DerivedMinimalTopicCatalog catalog =
                new DerivedMinimalTopicCatalog(YamlPresentationStyleCatalog.fromClasspath(), Map.of(Topic.PRICES, prices));

        assertThat(catalog.all()).hasSize(1);
        assertThat(catalog.get(Topic.PRICES).rules()).hasSize(1);
        assertThat(catalog.get(Topic.PRICES).rules().get(0).anyOf()).contains("inflation");
    }
}
