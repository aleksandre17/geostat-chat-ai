package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.SubdomainMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyUrlFilterTest {

    private final PolicyUrlFilter filter = new PolicyUrlFilter();

    @Test
    void curatedUrlBypassesExcludePatterns() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of("https://www.geostat.ge/ka/about-us"),
                List.of("www.geostat.ge"),
                SubdomainMode.none,
                List.of(),
                List.of("/about"),
                List.of());
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/about-us", policy)).isTrue();
    }

    @Test
    void subdomainListAllowsOnlyListedHosts() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of("www.geostat.ge", "geostat.ge"),
                SubdomainMode.list,
                List.of("cpi.geostat.ge"),
                List.of(),
                List.of());
        assertThat(filter.shouldEnqueue("https://cpi.geostat.ge/ka", policy)).isTrue();
        assertThat(filter.shouldEnqueue("https://gis.geostat.ge/ka", policy)).isFalse();
    }

    @Test
    void subdomainAllAllowsAnySubdomainOfAllowedHost() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of("geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of(),
                List.of());
        assertThat(filter.shouldEnqueue("https://census2024.geostat.ge/en", policy)).isTrue();
    }

    @Test
    void excludePatternBlocksPath() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of("www.geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of("/login"),
                List.of());
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/login", policy)).isFalse();
    }

    @Test
    void excludePatternBlocksMediaGalleryPaths() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of("www.geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of("/albums", "/video-gallery"),
                List.of());
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/albums", policy)).isFalse();
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/albums?page=2", policy)).isFalse();
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/video-gallery", policy)).isFalse();
        assertThat(filter.shouldEnqueue("https://www.geostat.ge/ka/news", policy)).isTrue();
    }

    @Test
    void shouldEnqueue_returnsFalse_forPaginatedUrl() {
        CorpusPolicyV2 policy = new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(),
                List.of("www.geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of("?page="),
                List.of());
        assertThat(filter.shouldEnqueue(
                "https://www.geostat.ge/ka/relationsOfCategory/100/post?page=2", policy))
                .isFalse();
        assertThat(filter.shouldEnqueue(
                "https://www.geostat.ge/ka/relationsOfCategory/100/post", policy))
                .isTrue();
    }
}
