package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.DefaultParseProfile;
import com.geostat.ingestion.parse.profile.JsoupContentExtractor;
import com.geostat.ingestion.parse.profile.MarkerBoilerplateStripper;
import com.geostat.ingestion.parse.profile.ParseProperties;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HtmlContentCleanerTest {

    private final HtmlContentCleaner cleaner = legacyCleaner();

    private static HtmlContentCleaner legacyCleaner() {
        ParseProperties disabled = new ParseProperties(new ParseProperties.Profile(false), "ops/config/corpus", "ops/eval/corpus-quality-gate.yaml");
        CorpusConfigurationLoader loader = Mockito.mock(CorpusConfigurationLoader.class);
        Mockito.when(loader.parseProfileFor(Mockito.anyString())).thenReturn(DefaultParseProfile.GEOSTAT_PORTAL);
        return new HtmlContentCleaner(
                new PageDisplayMetadataExtractor(),
                disabled,
                loader,
                new JsoupContentExtractor(new MarkerBoilerplateStripper(), new PageDisplayMetadataExtractor()));
    }

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
