package com.geostat.ingestion.parse.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.HtmlPageInput;
import java.time.Instant;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class GeostatNewsExtractionStrategyTest {

    private final GeostatNewsExtractionStrategy strategy = new GeostatNewsExtractionStrategy();

    @Test
    void extractsPublishedAt_fromJsonLd() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"NewsArticle","datePublished":"2025-03-15T10:00:00Z"}
                  </script>
                </head><body><article><h1>Title</h1><p>"""
                + "x".repeat(100)
                + """
                </p></article></body></html>""";
        CleanedDocument base = baseDoc(html);
        CleanedDocument doc = strategy.extract(new HtmlPageInput(html, "https://x.ge/news/1"), null, base);
        assertThat(doc.publishedAt()).isEqualTo(Instant.parse("2025-03-15T10:00:00Z"));
    }

    @Test
    void extractsPublishedAt_fromTimeElement() {
        String html = "<html><body><article><h1>T</h1>"
                + "<time datetime='2024-11-01T09:30:00Z'>1 November</time>"
                + "<p>"
                + "x".repeat(100)
                + "</p></article></body></html>";
        CleanedDocument base = baseDoc(html);
        CleanedDocument doc = strategy.extract(new HtmlPageInput(html, "https://x.ge/news/2"), null, base);
        assertThat(doc.publishedAt()).isEqualTo(Instant.parse("2024-11-01T09:30:00Z"));
    }

    @Test
    void publishedAt_isNull_whenNoDateSignalFound() {
        String html = "<html><body><article><h1>T</h1><p>" + "x".repeat(100) + "</p></article></body></html>";
        CleanedDocument base = baseDoc(html);
        CleanedDocument doc = strategy.extract(new HtmlPageInput(html, "https://x.ge/news/3"), null, base);
        assertThat(doc.publishedAt()).isNull();
    }

    private static CleanedDocument baseDoc(String html) {
        return new CleanedDocument(
                Jsoup.parse(html).title(),
                "x".repeat(100),
                "ka",
                List.of(),
                null,
                null,
                null,
                1,
                0,
                "https://x.ge/news/1",
                null,
                null,
                null);
    }
}
