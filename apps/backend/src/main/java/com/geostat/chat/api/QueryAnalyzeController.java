package com.geostat.chat.api;

import com.geostat.chat.api.dto.QueryAnalyzeRequest;
import com.geostat.chat.api.dto.QueryAnalyzeResponse;
import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.platform.enrichment.Entity;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RAG-U12 eval helper — exposes U07 pipeline output without chat generation. */
@RestController
@RequestMapping("/api/v1/chat")
public class QueryAnalyzeController {

    private final QueryUnderstandingPipeline queryUnderstandingPipeline;

    public QueryAnalyzeController(QueryUnderstandingPipeline queryUnderstandingPipeline) {
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
    }

    @PostMapping(value = "/query:analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QueryAnalyzeResponse> analyze(@RequestBody QueryAnalyzeRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AnalyzedQuery analyzed = queryUnderstandingPipeline.analyze(request.text(), request.locale());
        return ResponseEntity.ok(toResponse(analyzed));
    }

    private static QueryAnalyzeResponse toResponse(AnalyzedQuery analyzed) {
        List<QueryAnalyzeResponse.EntityView> entities = analyzed.entities().stream()
                .map(QueryAnalyzeController::toEntityView)
                .toList();
        return new QueryAnalyzeResponse(
                analyzed.intent().name().toLowerCase(),
                analyzed.normalized(),
                analyzed.retrievalText(),
                entities);
    }

    private static QueryAnalyzeResponse.EntityView toEntityView(Entity entity) {
        return new QueryAnalyzeResponse.EntityView(
                entity.type(), entity.value(), entity.normalizedForm(), entity.confidence());
    }
}
