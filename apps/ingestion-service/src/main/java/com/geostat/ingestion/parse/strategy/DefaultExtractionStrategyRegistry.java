package com.geostat.ingestion.parse.strategy;

import com.geostat.platform.parse.ExtractionStrategy;
import com.geostat.platform.parse.ExtractionStrategyRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Spring-managed registry of all {@link ExtractionStrategy} beans.
 *
 * <p>Spring auto-injects ALL {@code ExtractionStrategy} beans via the list constructor.
 * Strategies are evaluated in list order; first match wins.
 * Falls back to {@link YamlConfiguredStrategy} when no strategy matches.
 *
 * <p>To add a new strategy: create a new {@code @Component} implementing {@link ExtractionStrategy}.
 * No other code changes required.
 */
@Component
public class DefaultExtractionStrategyRegistry implements ExtractionStrategyRegistry {

    private final List<ExtractionStrategy> strategies;
    private final YamlConfiguredStrategy yamlFallback;

    public DefaultExtractionStrategyRegistry(
            List<ExtractionStrategy> strategies,
            YamlConfiguredStrategy yamlFallback) {
        this.strategies = strategies;
        this.yamlFallback = yamlFallback;
    }

    @Override
    public ExtractionStrategy resolve(String corpusName, String pageKind) {
        return strategies.stream()
                .filter(s -> !s.strategyId().equals(yamlFallback.strategyId()))
                .filter(s -> s.supports(corpusName, pageKind))
                .findFirst()
                .orElse(yamlFallback);
    }
}
