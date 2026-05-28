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

    @Test
    void respectRobotsTxtDefaultsToTrueWhenOmitted() {
        assertThat(CorpusPolicy.respectRobotsTxt(corpusWithPolicy(Map.of()))).isTrue();
    }

    @Test
    void respectRobotsTxtReadsCrawlSectionFlag() {
        CorpusEntity fromCrawl = corpusWithPolicy(Map.of("crawl", Map.of("respectRobotsTxt", true)));
        CorpusEntity disabledInCrawl = corpusWithPolicy(Map.of("crawl", Map.of("respectRobotsTxt", false)));

        assertThat(CorpusPolicy.respectRobotsTxt(fromCrawl)).isTrue();
        assertThat(CorpusPolicy.respectRobotsTxt(disabledInCrawl)).isFalse();
    }

    @Test
    void fullSitePolicyUsesUnlimitedPagesAndDepth() {
        CorpusEntity corpus = corpusWithPolicy(Map.of("crawlMode", "full-site"));

        assertThat(CorpusPolicy.isFullSite(corpus)).isTrue();
        assertThat(CorpusPolicy.maxPagesPerRun(corpus)).isEqualTo(Integer.MAX_VALUE);
        assertThat(CorpusPolicy.maxDepth(corpus)).isEqualTo(12);
        assertThat(CorpusPolicy.autoContinue(corpus)).isTrue();
    }

    @Test
    void boundedPolicyUsesDefaultsWhenOmitted() {
        CorpusEntity corpus = corpusWithPolicy(Map.of("crawlMode", "bounded"));

        assertThat(CorpusPolicy.maxPagesPerRun(corpus)).isEqualTo(50);
        assertThat(CorpusPolicy.maxDepth(corpus)).isEqualTo(2);
        assertThat(CorpusPolicy.autoContinue(corpus)).isFalse();
    }

    @Test
    void zeroMaxPagesMeansUnlimited() {
        CorpusEntity corpus = corpusWithPolicy(Map.of("maxPagesPerRun", 0));
        assertThat(CorpusPolicy.maxPagesPerRun(corpus)).isEqualTo(Integer.MAX_VALUE);
    }

    private static CorpusEntity corpusWithPolicy(Map<String, Object> policy) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setPolicy(policy);
        return corpus;
    }
}
