package com.geostat.ingestion.parse;

import com.geostat.ingestion.crawl.kind.HtmlSignalPageKindDetector;
import com.geostat.ingestion.crawl.kind.RoutingPageKindDetector;
import com.geostat.ingestion.crawl.kind.UrlPatternPageKindDetector;
import com.geostat.ingestion.parse.profile.JsoupContentExtractor;
import com.geostat.ingestion.parse.profile.MarkerBoilerplateStripper;
import com.geostat.ingestion.parse.strategy.YamlConfiguredStrategy;
import com.geostat.platform.crawl.PageKindDetector;
import com.geostat.platform.parse.BoilerplateStripper;
import com.geostat.platform.parse.ExtractionStrategyRegistry;
import org.mockito.Mockito;

/** Wires {@link JsoupContentExtractor} for unit tests without Spring context. */
public final class JsoupContentExtractorTestSupport {

    private JsoupContentExtractorTestSupport() {}

    public static JsoupContentExtractor yamlFallbackExtractor(
            BoilerplateStripper boilerplateStripper,
            PageDisplayMetadataExtractor displayMetadataExtractor,
            JsonLdExtractor jsonLdExtractor) {
        ExtractionStrategyRegistry registry = Mockito.mock(ExtractionStrategyRegistry.class);
        PageKindDetector pageKindDetector = new RoutingPageKindDetector(
                new UrlPatternPageKindDetector(), new HtmlSignalPageKindDetector());
        JsoupContentExtractor extractor = new JsoupContentExtractor(
                boilerplateStripper,
                displayMetadataExtractor,
                jsonLdExtractor,
                registry,
                pageKindDetector);
        YamlConfiguredStrategy fallback = new YamlConfiguredStrategy(extractor);
        Mockito.when(registry.resolve(Mockito.anyString(), Mockito.anyString())).thenReturn(fallback);
        return extractor;
    }

    public static JsoupContentExtractor yamlFallbackExtractor() {
        return yamlFallbackExtractor(
                new MarkerBoilerplateStripper(), new PageDisplayMetadataExtractor(), new JsonLdExtractor());
    }
}
