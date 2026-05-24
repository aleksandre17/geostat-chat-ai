package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class PageDisplayMetadataExtractorTest {

    private final PageDisplayMetadataExtractor extractor = new PageDisplayMetadataExtractor();

    @Test
    void prefersMetaDescriptionForDisplay() {
        var html = Jsoup.parse("""
                <html><head>
                  <meta name="description" content="Official inflation statistics for Georgia published monthly."/>
                </head><body><main><p>Table 1 2 3</p></main></body></html>
                """);

        PageDisplayMetadataExtractor.DisplayMetadata meta =
                extractor.extract(html, "Prices", List.of("Statistics"));

        assertThat(meta.metaDescription()).contains("inflation statistics");
        assertThat(meta.displayDescription()).isEqualTo(meta.metaDescription());
    }

    @Test
    void fallsBackToLeadParagraphWhenMetaMissing() {
        var html = Jsoup.parse("""
                <html><body><main>
                  <p>Consumer price index measures average price change for household goods and services.</p>
                </main></body></html>
                """);

        PageDisplayMetadataExtractor.DisplayMetadata meta =
                extractor.extract(html, "CPI", List.of());

        assertThat(meta.displayDescription()).contains("Consumer price index");
    }

    @Test
    void rejectsGeostatSiteWideOgDescriptionAndAccessibilityLead() {
        var html = Jsoup.parse("""
                <html><head>
                  <meta property="og:description" content="საჯარო სამართლის იურიდიული პირი - საქსტატი წარმოადგენს სტატისტიკის წარმოებისა და სტატისტიკური ინფორმაციის გავრცელების მიზნით შექმნილ დაწესებულებას."/>
                </head><body><main>
                  <p>ვებგვერდის ადაპტირებული ვერსია შეზღუდული შესაძლებლობის მქონე პირებისთვის შექმნილია UNDP-ის მხარდაჭერით.</p>
                </main></body></html>
                """);

        PageDisplayMetadataExtractor.DisplayMetadata meta = extractor.extract(
                html, "სამომხმარებლო ფასების ინდექსი (ინფლაცია)", List.of("ფასების სტატისტика"));

        assertThat(meta.displayDescription()).contains("ინფლაცია");
        assertThat(meta.displayDescription()).doesNotContain("ადაპტ");
        assertThat(meta.displayDescription()).doesNotContain("UNDP");
    }
}
