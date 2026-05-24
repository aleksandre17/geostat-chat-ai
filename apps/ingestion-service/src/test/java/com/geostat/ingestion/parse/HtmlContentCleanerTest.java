package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HtmlContentCleanerTest {

    private final HtmlContentCleaner cleaner =
            new HtmlContentCleaner(new PageDisplayMetadataExtractor());

    @Test
    void stripsNoiseAndKeepsMainText() {
        var html = Jsoup.parse("""
                <html lang="ka-GE">
                  <head>
                    <title>Test Page</title>
                    <meta name="description" content="Official test page for GeoStat statistics portal content extraction."/>
                  </head>
                  <body>
                    <nav>Menu</nav>
                    <main><p>Hello   world</p></main>
                    <script>ignore()</script>
                  </body>
                </html>
                """);

        HtmlContentCleaner.CleanedContent cleaned = cleaner.clean(html);

        assertThat(cleaned.title()).isEqualTo("Test Page");
        assertThat(cleaned.text()).isEqualTo("Hello world");
        assertThat(cleaned.language()).isEqualTo("ka");
        assertThat(cleaned.displayDescription()).isNotBlank();
    }
}
