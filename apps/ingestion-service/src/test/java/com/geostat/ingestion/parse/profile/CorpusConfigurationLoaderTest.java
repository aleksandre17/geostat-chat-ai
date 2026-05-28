package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.ParseProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CorpusConfigurationLoaderTest {

    private static CorpusConfigurationLoader loader;

    @BeforeAll
    static void setUp() {
        Path corpusDir = resolveCorpusConfigDir();
        assertThat(Files.isDirectory(corpusDir))
                .as("ops/config/corpus must exist at %s", corpusDir)
                .isTrue();
        loader = new CorpusConfigurationLoader(
                new ParseProperties(
                        new ParseProperties.Profile(true),
                        corpusDir.toString(),
                        "ops/eval/corpus-quality-gate.yaml"));
    }

    @Test
    void loadAllPolicies_discoversBothCorpora() {
        Set<String> corpora = loader.loadAllPolicies().stream()
                .map(CorpusPolicyV2::corpus)
                .collect(Collectors.toSet());

        assertThat(corpora).contains("geostat-portal", "agriculture-ge");
    }

    @Test
    void agricultureGeParseProfile_loadsHeadlessWithSelectorsAndPageKindRules() {
        ParseProfile profile = loader.parseProfileFor("agriculture-ge");

        assertThat(profile.renderMode()).isEqualTo(RenderMode.HEADLESS);
        assertThat(profile.rootSelectors()).contains(".chart-content");
        assertThat(profile.pageKindRules()).isNotEmpty();
    }

    @Test
    void geostatPortalParseProfile_defaultsToStaticRenderMode() {
        ParseProfile profile = loader.parseProfileFor("geostat-portal");

        assertThat(profile.renderMode()).isEqualTo(RenderMode.STATIC);
    }

    private static Path resolveCorpusConfigDir() {
        Path dir = Path.of(System.getProperty("user.dir", "."));
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("ops/config/corpus");
            if (Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
            dir = dir.getParent();
        }
        return Path.of("ops/config/corpus").normalize();
    }
}
