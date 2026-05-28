package com.geostat.ingestion.parse.strategy;

import com.geostat.ingestion.parse.profile.JsoupContentExtractor;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ExtractionStrategy;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import org.springframework.stereotype.Component;

/**
 * Fallback extraction strategy that delegates to the YAML-profile-driven
 * {@link JsoupContentExtractor} logic.
 *
 * <p>Used when no corpus/page-kind-specific strategy matches.
 * Preserves all existing extraction behavior unchanged.
 */
@Component
public class YamlConfiguredStrategy implements ExtractionStrategy {

    private final JsoupContentExtractor extractor;

    public YamlConfiguredStrategy(JsoupContentExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public String strategyId() {
        return "yaml-configured-fallback";
    }

    @Override
    public boolean supports(String corpusName, String pageKind) {
        return true; // catch-all fallback
    }

    @Override
    public boolean isDefaultFallback() {
        return true;
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        return extractor.extractInternal(page, profile);
    }
}
