package com.geostat.chat.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.retrieval.ResponseRoute;
import com.geostat.platform.retrieval.RetrievalConfidence;
import org.junit.jupiter.api.Test;

class ResponseRouterTest {

    private final ResponseRouter router = new ResponseRouter();

    @Test
    void high_confidence_routes_to_answer_with_citations() {
        ResponseRoute route = router.route(RetrievalConfidence.HIGH);
        assertThat(route).isEqualTo(ResponseRoute.ANSWER_WITH_CITATIONS);
    }

    @Test
    void medium_confidence_routes_to_answer_with_suggestions() {
        ResponseRoute route = router.route(RetrievalConfidence.MEDIUM);
        assertThat(route).isEqualTo(ResponseRoute.ANSWER_WITH_SUGGESTIONS);
    }

    @Test
    void low_confidence_routes_to_clarify() {
        ResponseRoute route = router.route(RetrievalConfidence.LOW);
        assertThat(route).isEqualTo(ResponseRoute.CLARIFY);
    }

    @Test
    void none_confidence_routes_to_refuse() {
        ResponseRoute route = router.route(RetrievalConfidence.NONE);
        assertThat(route).isEqualTo(ResponseRoute.REFUSE_SUGGEST_TOPICS);
    }

    @Test
    void should_generate_answer_for_high_and_medium() {
        assertThat(router.shouldGenerateAnswer(RetrievalConfidence.HIGH)).isTrue();
        assertThat(router.shouldGenerateAnswer(RetrievalConfidence.MEDIUM)).isTrue();
        assertThat(router.shouldGenerateAnswer(RetrievalConfidence.LOW)).isFalse();
        assertThat(router.shouldGenerateAnswer(RetrievalConfidence.NONE)).isFalse();
    }
}
