package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedCatalogTopicLabelResolverTest {

    @Mock
    private DerivedCatalogReader derivedCatalogReader;

    private DerivedCatalogTopicLabelResolver resolver;

    @BeforeEach
    void setUp() {
        CatalogProperties properties = new CatalogProperties();
        properties.setMaxClusters(2);
        resolver = new DerivedCatalogTopicLabelResolver(derivedCatalogReader, properties);
    }

    @Test
    void usesClusterLabelsWhenMatched() {
        when(derivedCatalogReader.matchClusters("inflation", "en", 2))
                .thenReturn(new com.geostat.chat.domain.catalog.DerivedClusterMatch(
                        List.of(new DerivedTopicCluster(UUID.randomUUID(), "ინფლაცია", "Inflation"))));

        CatalogTopicLabelResolver.Labels labels =
                resolver.resolve(List.of(Topic.GENERAL), "inflation", "en", false);

        assertThat(labels.primary()).isEqualTo("Inflation");
        assertThat(labels.all()).containsExactly("Inflation");
    }

    @Test
    void fallsBackToDetectedTopicNamesWhenNoClusterMatch() {
        when(derivedCatalogReader.matchClusters("x", "ka", 2))
                .thenReturn(new com.geostat.chat.domain.catalog.DerivedClusterMatch(List.of()));

        CatalogTopicLabelResolver.Labels labels =
                resolver.resolve(List.of(Topic.PRICES), "x", "ka", true);

        assertThat(labels.primary()).isEqualTo("PRICES");
    }
}
