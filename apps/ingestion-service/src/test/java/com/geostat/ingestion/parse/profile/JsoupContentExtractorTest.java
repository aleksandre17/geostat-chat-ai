package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.parse.HtmlPageInput;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class JsoupContentExtractorTest {

    private final JsoupContentExtractor extractor =
            new JsoupContentExtractor(new MarkerBoilerplateStripper(), new com.geostat.ingestion.parse.PageDisplayMetadataExtractor());

    @Test
    void stripsAccessibilityBoilerplateFromMainContent() {
        var html = Jsoup.parse("""
                <html lang="ka-GE">
                  <head><title>ინფლაცია</title></head>
                  <body>
                    <nav>Menu</nav>
                    <main>
                      <p>ვებგვერდის ადაპტირებული ვერსია საქსტატის პორტალზე.</p>
                      <p>საქართველოში ინფლაციის დონე 2024 წელს შემცირდა.</p>
                      <h2>მაჩვენებლები</h2>
                    </main>
                  </body>
                </html>
                """);

        var doc = extractor.extract(new HtmlPageInput(html.html(), "https://www.geostat.ge/ka/inflation"), DefaultParseProfile.GEOSTAT_PORTAL);

        assertThat(doc.title()).isEqualTo("ინფლაცია");
        assertThat(doc.bodyText()).contains("ინფლაციის დონე");
        assertThat(doc.bodyText()).doesNotContain("ადაპტირებული");
        assertThat(doc.sectionPath()).contains("მაჩვენებლები");
    }

    @Test
    void preservesTableTextWhenEnabled() {
        var html = Jsoup.parse("""
                <html lang="en">
                  <head><title>Dataset</title></head>
                  <body>
                    <main>
                      <table><tr><th>Year</th><th>Value</th></tr><tr><td>2024</td><td>3.1</td></tr></table>
                    </main>
                  </body>
                </html>
                """);

        var doc = extractor.extract(new HtmlPageInput(html.html(), "https://www.geostat.ge/en/data"), DefaultParseProfile.GEOSTAT_PORTAL);

        assertThat(doc.bodyText()).contains("2024").contains("3.1");
    }
}
