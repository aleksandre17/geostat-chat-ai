package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.query.QueryIntentKind;
import org.junit.jupiter.api.Test;

class HeuristicIntentClassifierTest {

    private final HeuristicIntentClassifier classifier = new HeuristicIntentClassifier();

    @Test
    void detectsNavigationIntent() {
        assertThat(classifier.classify("სად ვნახო მშპ", "სად ვნახო მშპ", "ka"))
                .isEqualTo(QueryIntentKind.NAVIGATION);
    }

    @Test
    void defaultsToLookupForKeywords() {
        assertThat(classifier.classify("statistika", "statistika", "ka")).isEqualTo(QueryIntentKind.LOOKUP);
    }
}
