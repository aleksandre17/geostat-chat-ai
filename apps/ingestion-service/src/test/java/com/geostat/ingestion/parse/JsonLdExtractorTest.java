package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class JsonLdExtractorTest {

    private final JsonLdExtractor extractor = new JsonLdExtractor();

    @Test
    void extract_returnsPublishedAt_fromDatePublished() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"NewsArticle","datePublished":"2025-03-15T10:00:00Z"}
                  </script>
                </head><body></body></html>""";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.publishedAt()).isEqualTo("2025-03-15T10:00:00Z");
    }

    @Test
    void extract_returnsBreadcrumb_fromBreadcrumbList() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"BreadcrumbList","itemListElement":[
                      {"@type":"ListItem","position":1,"name":"Home"},
                      {"@type":"ListItem","position":2,"name":"News"}
                    ]}
                  </script>
                </head><body></body></html>""";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.navBreadcrumb()).isEqualTo("Home > News");
    }

    @Test
    void extract_returnsSchemaType_fromType() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@type":"Dataset","name":"GDP"}
                  </script>
                </head><body></body></html>""";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.schemaType()).isEqualTo("Dataset");
    }

    @Test
    void extract_handlesGraphArray() {
        String html = """
                <html><head>
                  <script type="application/ld+json">
                    {"@graph":[
                      {},
                      {"@type":"NewsArticle","datePublished":"2024-01-01T00:00:00Z","description":"Summary text"}
                    ]}
                  </script>
                </head><body></body></html>""";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.publishedAt()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(result.description()).isEqualTo("Summary text");
        assertThat(result.schemaType()).isEqualTo("NewsArticle");
    }

    @Test
    void extract_returnsEmptyResult_forMalformedJson() {
        String html = """
                <html><head>
                  <script type="application/ld+json">{not valid json</script>
                </head><body></body></html>""";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.hasAnyData()).isFalse();
    }

    @Test
    void extract_returnsEmptyResult_whenNoLdJson() {
        String html = "<html><head><title>Plain</title></head><body><p>Text</p></body></html>";

        JsonLdExtractor.JsonLdResult result = extractor.extract(Jsoup.parse(html));

        assertThat(result.hasAnyData()).isFalse();
    }
}
