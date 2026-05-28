package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.curation.CurationOverrideService;
import com.geostat.platform.contracts.feedback.FeedbackScoreEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnExpression(
        "${geostat.ingestion.feedback.score-boost-enabled:false} && ${geostat.ingestion.events.enabled:false}")
public class FeedbackScoreListener {

    private final CurationOverrideService curationOverrideService;

    public FeedbackScoreListener(CurationOverrideService curationOverrideService) {
        this.curationOverrideService = curationOverrideService;
    }

    @RabbitListener(queues = "geostat.ingestion.feedback-score")
    public void onFeedbackScore(FeedbackScoreEvent event) {
        curationOverrideService.applyScoreBoost(
                event.documentId(), event.corpusId(), event.scoreDelta());
    }
}
