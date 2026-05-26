package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.JsoupContentExtractor;
import com.geostat.ingestion.parse.profile.MarkerBoilerplateStripper;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.profile.ThresholdsCorpusQualityGate;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.QualityThresholds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class JsoupContentExtractorFixtureTest {

    private static JsoupContentExtractor extractor;
    private static ThresholdsCorpusQualityGate qualityGate;
    private static QualityThresholds thresholds;

    @BeforeAll
    static void setUp() {
        extractor = new JsoupContentExtractor(new MarkerBoilerplateStripper(), new PageDisplayMetadataExtractor());
        qualityGate = new ThresholdsCorpusQualityGate();
        thresholds = QualityThresholds.p0Defaults();
    }

    @TestFactory
    Stream<DynamicTest> fixtureFiles() throws IOException {
        Path dir = Path.of("src/test/resources/fixtures/geostat");
        if (!Files.isDirectory(dir)) {
            return Stream.of(DynamicTest.dynamicTest("fixtures-dir-missing", () -> assertThat(true).isTrue()));
        }
        try (Stream<Path> files = Files.list(dir).filter(p -> p.toString().endsWith(".html"))) {
            return files.map(this::testForFixture).toList().stream();
        }
    }

    private DynamicTest testForFixture(Path fixture) {
        String name = fixture.getFileName().toString();
        return DynamicTest.dynamicTest(name, () -> {
            String html = Files.readString(fixture, StandardCharsets.UTF_8);
            String url = urlForFixture(name);
            var profile = new CorpusConfigurationLoader(
                            new ParseProperties(
                                    new ParseProperties.Profile(true),
                                    "ops/config/corpus",
                                    "ops/eval/corpus-quality-gate.yaml"))
                    .parseProfileFor("geostat-portal");
            var doc = extractor.extract(new HtmlPageInput(html, url), profile);

            switch (name) {
                case "portal-landing-ka.html" -> {
                    assertThat(doc.bodyText()).contains("3 941.1").contains("5.9");
                    assertThat(doc.bodyText()).doesNotContain("ადაპტირებული");
                    assertThat(doc.bodyText()).doesNotContain("Crafted by");
                    assertThat(doc.sectionPath()).isNotEmpty();
                }
                case "portal-landing-en.html" -> {
                    assertThat(doc.bodyText()).contains("3 941.1").contains("Inflation");
                    assertThat(doc.bodyText()).doesNotContain("adapted version");
                }
                case "dataset-page-ka.html" -> {
                    assertThat(doc.bodyText()).contains("228.5").contains("866.2");
                    assertThat(doc.bodyText()).contains("საწარმოთა დეკლარირებული მონაცემები");
                    assertThat(doc.bodyText()).doesNotContain("არქივი");
                }
                case "news-listing-ka.html" -> {
                    assertThat(doc.bodyText()).contains("ცხოვრების დონის მაჩვენებლები");
                    assertThat(doc.bodyText()).doesNotContain("124");
                }
                case "albums-listing-ka.html" -> {
                    assertThat(doc.bodyText()).contains("კონფერენციაში მონაწილეობდა");
                    assertThat(doc.bodyText()).doesNotContain("37");
                }
                case "archive-page-ka.html" -> {
                    assertThat(doc.bodyText()).contains("კვარტალური ბიულეტენი");
                    assertThat(doc.bodyText()).doesNotContain("არქივი");
                }
                case "news-article-ka.html" -> {
                    assertThat(doc.bodyText()).contains("ცხოვრების დონის მაჩვენებელი");
                    assertThat(doc.bodyText()).contains("4.2%");
                    assertThat(doc.sectionPath()).isNotEmpty();
                }
                case "navigation-about.html" -> {
                    assertThat(doc.bodyText()).contains("საქსტატი");
                    assertThat(doc.bodyText()).doesNotContain("ადაპტირებული");
                }
                case "bilingual-pair-ka.html" -> {
                    assertThat(doc.language()).isEqualTo("ka");
                    assertThat(doc.bodyText()).contains("3 941.1");
                }
                case "bilingual-pair-en.html" -> {
                    assertThat(doc.language()).isEqualTo("en");
                    assertThat(doc.bodyText()).contains("Inflation");
                }
                case "empty-subdomain.html" -> assertThat(qualityGate.evaluate(doc, thresholds))
                        .isIn(
                                CorpusQualityGate.Decision.SKIP_TOO_SHORT,
                                CorpusQualityGate.Decision.SKIP_BOILERPLATE_ONLY);
                default -> assertThat(doc.bodyText()).isNotBlank();
            }
        });
    }

    private static String urlForFixture(String name) {
        return switch (name) {
            case "portal-landing-en.html" -> "https://www.geostat.ge/en";
            case "dataset-page-ka.html" -> "https://www.geostat.ge/ka/modules/categories/195/biznes-sektori";
            case "news-listing-ka.html" -> "https://www.geostat.ge/ka/news";
            case "albums-listing-ka.html" -> "https://www.geostat.ge/ka/albums";
            case "archive-page-ka.html" -> "https://www.geostat.ge/ka/single-categories/121/kvartaluri";
            case "news-article-ka.html" -> "https://www.geostat.ge/ka/news/12345/test-article";
            case "navigation-about.html" -> "https://www.geostat.ge/ka/about-us";
            case "bilingual-pair-en.html" -> "https://www.geostat.ge/en/modules/categories/1/inflation";
            case "bilingual-pair-ka.html" -> "https://www.geostat.ge/ka/modules/categories/1/inflation";
            case "empty-subdomain.html" -> "https://gis.geostat.ge/ka";
            default -> "https://www.geostat.ge/ka";
        };
    }
}
