package com.geostat.platform.parse;

/**
 * Resolves the appropriate {@link ExtractionStrategy} for a given corpus and page kind.
 *
 * <p>Falls back to a YAML-configured default if no specific strategy matches.
 * Implementations must always return a non-null strategy.
 */
public interface ExtractionStrategyRegistry {

    /**
     * Resolve extraction strategy for the given corpus + page kind.
     * Never returns null — falls back to {@code YamlConfiguredStrategy} if no match.
     */
    ExtractionStrategy resolve(String corpusName, String pageKind);
}
