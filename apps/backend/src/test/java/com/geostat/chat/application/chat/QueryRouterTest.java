package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.chat.QueryIntent;
import org.junit.jupiter.api.Test;

class QueryRouterTest {

    private final QueryRouter router = new QueryRouter();

    @Test
    void routesConceptQuestions() {
        assertThat(router.route("What is inflation?", "what is inflation?")).isEqualTo(QueryIntent.CONCEPT);
        assertThat(router.route("რა არის ინფლაცია", "რა არის ინფლაცია")).isEqualTo(QueryIntent.CONCEPT);
    }

    @Test
    void routesGeneralQueriesToConceptForRag() {
        assertThat(router.route("statistika", "statistika")).isEqualTo(QueryIntent.CONCEPT);
        assertThat(router.route("employment rate", "employment rate")).isEqualTo(QueryIntent.CONCEPT);
    }

    @Test
    void routesExplicitNavigation() {
        assertThat(router.route("open the portal", "open the portal")).isEqualTo(QueryIntent.DATA_REQUEST);
        assertThat(router.route("statistics page", "statistics page")).isEqualTo(QueryIntent.NAVIGATE);
    }
}
