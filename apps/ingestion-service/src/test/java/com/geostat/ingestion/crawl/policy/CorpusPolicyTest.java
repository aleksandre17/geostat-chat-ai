package com.geostat.ingestion.crawl.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorpusPolicyTest {

    @Test
    void excludePatternsBlockMatchingPaths() {
        CorpusEntity corpus = corpusWithPolicy(Map.of(
                "excludePatterns", java.util.List.of("/login", "/admin")));

        assertThat(CorpusPolicy.isUrlAllowed(corpus, "https://www.geostat.ge/ka/news")).isTrue();
        assertThat(CorpusPolicy.isUrlAllowed(corpus, "https://www.geostat.ge/login")).isFalse();
    }

    @Test
    void includePatternsRequireMatchWhenPresent() {
        CorpusEntity corpus = corpusWithPolicy(Map.of(
                "includePatterns", java.util.List.of("/ka", "/en")));

        assertThat(CorpusPolicy.isUrlAllowed(corpus, "https://www.geostat.ge/ka/page")).isTrue();
        assertThat(CorpusPolicy.isUrlAllowed(corpus, "https://www.geostat.ge/private/page")).isFalse();
    }

    @Test
    void respectRobotsTxtReadsPolicyFlag() {
        CorpusEntity enabled = corpusWithPolicy(Map.of("respectRobotsTxt", true));
        CorpusEntity disabled = corpusWithPolicy(Map.of("respectRobotsTxt", false));

        assertThat(CorpusPolicy.respectRobotsTxt(enabled)).isTrue();
        assertThat(CorpusPolicy.respectRobotsTxt(disabled)).isFalse();
    }

    private static CorpusEntity corpusWithPolicy(Map<String, Object> policy) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setPolicy(policy);
        return corpus;
    }
}
