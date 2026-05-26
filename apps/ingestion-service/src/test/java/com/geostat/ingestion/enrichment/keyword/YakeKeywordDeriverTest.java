package com.geostat.ingestion.enrichment.keyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.enrichment.keyword.yake.YakeKeywordExtractor;
import org.junit.jupiter.api.Test;

class YakeKeywordDeriverTest {

    private final YakeKeywordExtractor extractor = new YakeKeywordExtractor(3);

    @Test
    void extractEnglishKeywordsPrefersRepeatedDomainTerms() {
        String text =
                """
                Consumer Price Index CPI measures inflation in 2024.
                CPI statistics are published monthly by Geostat.
                Inflation trends for CPI remain important for policy.
                """;

        var keywords = extractor.extract(text, 15);

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.stream().map(String::toLowerCase))
                .anyMatch(kw -> kw.contains("cpi") || kw.contains("inflation"));
    }

    @Test
    void extractGeorgianKeywordsReturnsNonEmptyList() {
        String text =
                """
                ფასების ინფლაციის ინდექსი 2024 წელს გაიზარდა.
                საქართველოს სტატისტიკის ეროვნული სამსახური CPI მონაცემებს აქვეყნებს.
                """;

        var keywords = extractor.extract(text, 15);

        assertThat(keywords).isNotEmpty();
        assertThat(String.join(" ", keywords)).containsAnyOf("2024", "CPI", "ფასების");
    }

    @Test
    void extractRespectsTopNAndDedup() {
        String text = "CPI CPI CPI inflation inflation inflation statistics statistics statistics";

        var keywords = extractor.extract(text, 3);

        assertThat(keywords).hasSizeLessThanOrEqualTo(3);
        assertThat(keywords.stream().map(String::toLowerCase).distinct()).hasSize(keywords.size());
    }
}
