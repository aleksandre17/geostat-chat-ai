package com.geostat.ingestion.enrichment.pagekind;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.enrichment.DocumentContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageKindUrlHeuristicTest {

    @Test
    void classifiesNewsFromUrl() {
        var result = PageKindUrlHeuristic.classify(context("https://www.geostat.ge/ka/news/2024-cpi"), "v1");
        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(PageKindValues.NEWS);
    }

    @Test
    void classifiesDatasetFromFileExtension() {
        var result = PageKindUrlHeuristic.classify(
                context("https://www.geostat.ge/en/statistics/data.csv"), "v1");
        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(PageKindValues.DATASET);
    }

    @Test
    void classifiesNavigationFromAboutPath() {
        var result = PageKindUrlHeuristic.classify(context("https://www.geostat.ge/ka/about/contact"), "v1");
        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(PageKindValues.NAVIGATION);
    }

    @Test
    void classifiesPortalLanding() {
        var result = PageKindUrlHeuristic.classify(context("https://www.geostat.ge/ka/modules/categories/"), "v1");
        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(PageKindValues.PORTAL);
    }

    @Test
    void returnsEmptyForAmbiguousStatisticsPage() {
        var result = PageKindUrlHeuristic.classify(
                context("https://www.geostat.ge/ka/statistics/indicators/cpi"), "v1");
        assertThat(result).isEmpty();
    }

    private static DocumentContext context(String url) {
        return new DocumentContext(UUID.randomUUID(), url, "Title", "body", "ka", "Section");
    }
}
