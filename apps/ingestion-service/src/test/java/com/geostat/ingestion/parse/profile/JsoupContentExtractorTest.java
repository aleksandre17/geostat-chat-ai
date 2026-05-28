package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.parse.JsoupContentExtractorTestSupport;
import com.geostat.platform.parse.HtmlPageInput;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class JsoupContentExtractorTest {

    private final JsoupContentExtractor extractor = JsoupContentExtractorTestSupport.yamlFallbackExtractor();

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

    @Test
    void extractBody_noDuplication_when_pInsideLi() {
        String html = """
                <html><body><article>
                  <ul>
                    <li><p>ბუნებრივი მოძრაობა 1.2%.</p></li>
                    <li>სხვა ელემენტი</li>
                  </ul>
                  <p>ზოგადი ინფორმაცია.</p>
                </article></body></html>""";
        var doc = extractor.extract(new HtmlPageInput(html, "https://x.ge"), DefaultParseProfile.GEOSTAT_PORTAL);
        String body = doc.bodyText();

        assertThat(countOccurrences(body, "ბუნებრივი მოძრაობა 1.2%")).isEqualTo(1);
        assertThat(body).contains("ბუნებრივი მოძრაობა 1.2%.");
        assertThat(body).contains("სხვა ელემენტი");
        assertThat(body).contains("ზოგადი ინფორმაცია.");
    }

    @Test
    void extract_removesTemplateElement() {
        String html = """
                <html><body><main>
                  <p>სტატია GDP-ის შესახებ 8.7%.</p>
                  <template id="card-tpl">
                    <li><a href="{{url}}">{{title}}</a></li>
                  </template>
                </main></body></html>""";
        var doc = extractor.extract(new HtmlPageInput(html, "https://x.ge"), DefaultParseProfile.GEOSTAT_PORTAL);

        assertThat(doc.bodyText()).doesNotContain("{{title}}");
        assertThat(doc.bodyText()).doesNotContain("{{url}}");
        assertThat(doc.bodyText()).contains("8.7%");
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) != -1) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}
