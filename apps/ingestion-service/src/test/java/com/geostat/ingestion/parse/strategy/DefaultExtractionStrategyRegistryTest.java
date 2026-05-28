package com.geostat.ingestion.parse.strategy;

import com.geostat.platform.parse.ExtractionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DefaultExtractionStrategyRegistryTest {

    @Mock YamlConfiguredStrategy yaml;
    @Mock ExtractionStrategy newsStrategy;
    DefaultExtractionStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(yaml.strategyId()).thenReturn("yaml-configured-fallback");
        when(newsStrategy.strategyId()).thenReturn("geostat-news");
        when(newsStrategy.supports("geostat-portal", "news")).thenReturn(true);
        when(newsStrategy.supports("geostat-portal", "unknown")).thenReturn(false);
        registry = new DefaultExtractionStrategyRegistry(List.of(newsStrategy, yaml), yaml);
    }

    @Test
    void resolvesGeostatNews_forNewsPageKind() {
        assertThat(registry.resolve("geostat-portal", "news").strategyId())
                .isEqualTo("geostat-news");
    }

    @Test
    void fallsBackToYaml_forUnknownPageKind() {
        assertThat(registry.resolve("geostat-portal", "unknown").strategyId())
                .isEqualTo("yaml-configured-fallback");
    }

    @Test
    void fallsBackToYaml_forUnknownCorpus() {
        assertThat(registry.resolve("other-corpus", "news").strategyId())
                .isEqualTo("yaml-configured-fallback");
    }
}
