package com.geostat.ingestion.crawl.kind;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.PageKindRule;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrlPatternPageKindDetectorTest {

    UrlPatternPageKindDetector detector = new UrlPatternPageKindDetector();

    ParseProfile profileWithRules() {
        List<PageKindRule> rules = List.of(
                new PageKindRule("news", List.of("/single-news/"), 10),
                new PageKindRule("portal", List.of("^https://www\\.geostat\\.ge/ka$"), 20),
                new PageKindRule("dataset", List.of("/open-data/"), 10),
                new PageKindRule("category", List.of("/modules/categories/"), 5));
        return new ParseProfile(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of(),
                null,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                rules,
                "unknown",
                "firstMatch");
    }

    @Test
    void detects_news_bySingleNewsUrlPattern() {
        FetchedPage page = page("https://www.geostat.ge/ka/single-news/1756/title");
        assertThat(detector.detect(page, profileWithRules())).isEqualTo(PageKind.NEWS);
    }

    @Test
    void detects_portal_byExactRegex() {
        FetchedPage page = page("https://www.geostat.ge/ka");
        assertThat(detector.detect(page, profileWithRules())).isEqualTo(PageKind.PORTAL);
    }

    @Test
    void returns_defaultPageKind_forUnrecognizedUrl() {
        FetchedPage page = page("https://www.geostat.ge/ka/some-unknown-path/123");
        assertThat(detector.detect(page, profileWithRules())).isEqualTo(PageKind.UNKNOWN);
    }

    @Test
    void highPriorityRule_winsWhenMultipleMatch() {
        FetchedPage page = page("https://www.geostat.ge/ka/open-data/modules/categories/1");
        assertThat(detector.detect(page, profileWithRules())).isEqualTo(PageKind.DATASET);
    }

    private FetchedPage page(String url) {
        return new FetchedPage(url, "", 200, "text/html", RenderMode.STATIC);
    }
}
