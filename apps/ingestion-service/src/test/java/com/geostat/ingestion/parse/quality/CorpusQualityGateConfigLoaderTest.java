package com.geostat.ingestion.parse.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateDefinition;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusQualityGateConfigLoaderTest {

    @Test
    void parsesAllOperatorsForTarget() {
        assertThat(GateTarget.parse("<= 0.05").evaluate(0.05)).isTrue();
        assertThat(GateTarget.parse("<= 0.05").evaluate(0.06)).isFalse();
        assertThat(GateTarget.parse(">= 0.95").evaluate(0.95)).isTrue();
        assertThat(GateTarget.parse(">= 0.95").evaluate(0.94)).isFalse();
        assertThat(GateTarget.parse("< 0.5").evaluate(0.5)).isFalse();
        assertThat(GateTarget.parse("> 0.5").evaluate(0.5)).isFalse();
        assertThat(GateTarget.parse("= 0.42").evaluate(0.42)).isTrue();
    }

    @Test
    void rejectsMalformedTarget() {
        assertThatThrownBy(() -> GateTarget.parse("approximately 0.5"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GateTarget.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsGateDefinitionsFromYaml(@TempDir Path tempDir) throws Exception {
        Path yaml = tempDir.resolve("corpus-quality-gate.yaml");
        Files.writeString(
                yaml,
                """
                corpus: test-corpus
                gates:
                  - id: boilerplate_ratio
                    description: "boilerplate share"
                    metric:
                      sql: |
                        SELECT 0.0
                    target: "<= 0.05"
                    currentBaseline: 0.86
                    blocks: [enrichment_backfill]
                  - id: chunk_coverage
                    metric:
                      sql: |
                        SELECT 1.0
                    target: ">= 0.95"
                """);

        CorpusQualityGateConfigLoader loader = new CorpusQualityGateConfigLoader(
                new ParseProperties(new ParseProperties.Profile(true), "ops/config/corpus", yaml.toAbsolutePath().toString()));

        CorpusQualityGateConfig config = loader.load();
        assertThat(config.corpus()).isEqualTo("test-corpus");
        assertThat(config.gates()).hasSize(2);

        GateDefinition first = config.gates().get(0);
        assertThat(first.id()).isEqualTo("boilerplate_ratio");
        assertThat(first.sql()).contains("SELECT 0.0");
        assertThat(first.target().asText()).isEqualTo("<= 0.05");
        assertThat(first.blocks()).containsExactly("enrichment_backfill");
        assertThat(first.currentBaseline()).isEqualTo(0.86);
    }

    @Test
    void returnsEmptyConfigWhenYamlMissing() {
        CorpusQualityGateConfigLoader loader = new CorpusQualityGateConfigLoader(
                new ParseProperties(
                        new ParseProperties.Profile(true),
                        "ops/config/corpus",
                        "ops/eval/does-not-exist.yaml"));
        assertThat(loader.load().gates()).isEmpty();
    }

    @Test
    void skipsGatesWithInvalidTargetOrMissingSql() throws Exception {
        Path yaml = Files.createTempFile("gate", ".yaml");
        Files.writeString(
                yaml,
                """
                corpus: test
                gates:
                  - id: no_sql
                    target: "<= 0.05"
                  - id: bad_target
                    metric:
                      sql: SELECT 0.0
                    target: "approximately 0.5"
                  - id: ok
                    metric:
                      sql: SELECT 1.0
                    target: ">= 0.99"
                """);
        try {
            CorpusQualityGateConfigLoader loader = new CorpusQualityGateConfigLoader(
                    new ParseProperties(
                            new ParseProperties.Profile(true),
                            "ops/config/corpus",
                            yaml.toAbsolutePath().toString()));
            assertThat(loader.load().gates()).extracting(GateDefinition::id).containsExactly("ok");
        } finally {
            Files.deleteIfExists(yaml);
        }
    }
}
