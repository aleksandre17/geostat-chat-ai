package com.geostat.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.chat.api.dto.QueryAnalyzeRequest;
import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.geostat.platform.enrichment.Entity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class QueryAnalyzeControllerTest {

    @Mock
    private QueryUnderstandingPipeline queryUnderstandingPipeline;

    @InjectMocks
    private QueryAnalyzeController controller;

    @Test
    void analyzeReturnsIntentAndEntities() {
        when(queryUnderstandingPipeline.analyze("ინფლაცია", "ka"))
                .thenReturn(new AnalyzedQuery(
                        "ინფლაცია",
                        "ინფლაცია",
                        "ინფლაცია",
                        "ინფლაცია",
                        QueryIntentKind.DEFINITION,
                        "ka",
                        List.of(new Entity("indicator", "ინფლაცია", "inflation", 0.9)),
                        List.of()));

        ResponseEntity<?> response = controller.analyze(new QueryAnalyzeRequest("ინფლაცია", "ka"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var body = (com.geostat.chat.api.dto.QueryAnalyzeResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.intent()).isEqualTo("definition");
        assertThat(body.entities()).hasSize(1);
    }
}
