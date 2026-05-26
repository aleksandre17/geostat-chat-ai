package com.geostat.ingestion.enrichment.topic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicClusterCountTest {

    @Test
    void forDocumentCountUsesSqrtHeuristic() {
        assertThat(TopicClusterCount.forDocumentCount(1000)).isEqualTo(10);
        assertThat(TopicClusterCount.forDocumentCount(100)).isEqualTo(3);
    }

    @Test
    void forDocumentCountReturnsZeroWhenTooFewDocuments() {
        assertThat(TopicClusterCount.forDocumentCount(0)).isEqualTo(0);
        assertThat(TopicClusterCount.forDocumentCount(1)).isEqualTo(0);
    }

    @Test
    void forDocumentCountNeverExceedsDocumentCount() {
        assertThat(TopicClusterCount.forDocumentCount(3)).isEqualTo(2);
    }
}
