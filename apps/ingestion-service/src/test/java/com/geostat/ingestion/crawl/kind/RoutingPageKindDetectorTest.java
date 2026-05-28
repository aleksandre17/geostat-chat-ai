package com.geostat.ingestion.crawl.kind;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.PageKindRule;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingPageKindDetectorTest {

    UrlPatternPageKindDetector urlDet = new UrlPatternPageKindDetector();
    HtmlSignalPageKindDetector htmlDet = new HtmlSignalPageKindDetector();
    RoutingPageKindDetector router = new RoutingPageKindDetector(urlDet, htmlDet);

    @Test
    void prefersUrlPattern_overHtmlSignal() {
        String html = "<html><head><meta property='og:type' content='article'/></head></html>";
        FetchedPage page = new FetchedPage(
                "https://www.geostat.ge/ka/single-news/1", html, 200, "text/html", RenderMode.STATIC);
        assertThat(router.detect(page, profileWithNewsRule())).isEqualTo(PageKind.NEWS);
    }

    @Test
    void fallsBackToHtml_whenUrlGivesUnknown() {
        String html = "<html><head><meta property='og:type' content='article'/></head></html>";
        FetchedPage page = new FetchedPage(
                "https://www.geostat.ge/ka/some-other", html, 200, "text/html", RenderMode.STATIC);
        assertThat(router.detect(page, profileWithNewsRule())).isEqualTo(PageKind.NEWS);
    }

    private ParseProfile profileWithNewsRule() {
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
                List.of(new PageKindRule("news", List.of("/single-news/"), 10)),
                "unknown",
                "firstMatch");
    }
}
