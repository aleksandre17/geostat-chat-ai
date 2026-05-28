package com.geostat.ingestion.crawl.kind;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlSignalPageKindDetectorTest {

    HtmlSignalPageKindDetector detector = new HtmlSignalPageKindDetector();
    ParseProfile emptyProfile = new ParseProfile(
            "test",
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
            List.of(),
            "unknown",
            "firstMatch");

    @Test
    void detects_news_fromOgTypeArticle() {
        FetchedPage page = page(
                "https://x.ge/p",
                "<html><head><meta property='og:type' content='article'/></head></html>");
        assertThat(detector.detect(page, emptyProfile)).isEqualTo(PageKind.NEWS);
    }

    @Test
    void detects_dataset_fromJsonLdType() {
        String html = """
                <html><head><script type='application/ld+json'>
                  {"@type": "Dataset", "name": "CPI Data"}
                </script></head></html>""";
        assertThat(detector.detect(page("https://x.ge/p", html), emptyProfile))
                .isEqualTo(PageKind.DATASET);
    }

    @Test
    void returns_unknown_forNoSignals() {
        FetchedPage page = page("https://x.ge/p", "<html><body><p>Text</p></body></html>");
        assertThat(detector.detect(page, emptyProfile)).isEqualTo(PageKind.UNKNOWN);
    }

    private FetchedPage page(String url, String html) {
        return new FetchedPage(url, html, 200, "text/html", RenderMode.STATIC);
    }
}
