package com.geostat.retrieval.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationEligibilityFilterTest {

    @Test
    void legacyMissingServeStateIsCitable() {
        assertThat(CitationEligibilityFilter.isCitable(null, "dataset")).isTrue();
    }

    @Test
    void navigationIsNeverCitable() {
        assertThat(CitationEligibilityFilter.isCitable("live", "navigation")).isFalse();
    }

    @Test
    void droppedIsNotCitable() {
        assertThat(CitationEligibilityFilter.isCitable("dropped", "report")).isFalse();
    }

    @Test
    void partialEnrichedIsCitable() {
        assertThat(CitationEligibilityFilter.isCitable("partial_enriched", "report")).isTrue();
    }
}
