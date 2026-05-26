package com.geostat.ingestion.enrichment.topic;

import com.geostat.ingestion.persistence.repository.CorpusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.enrichment",
        name = "topic-scheduler-enabled",
        havingValue = "true")
public class TopicBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(TopicBatchScheduler.class);

    private final CorpusRepository corpusRepository;
    private final TopicRemineService topicRemineService;

    public TopicBatchScheduler(CorpusRepository corpusRepository, TopicRemineService topicRemineService) {
        this.corpusRepository = corpusRepository;
        this.topicRemineService = topicRemineService;
    }

    @Scheduled(cron = "${geostat.ingestion.enrichment.topic-cron:0 0 4 * * *}")
    public void remineAllCorpora() {
        corpusRepository.findAll().forEach(corpus -> {
            try {
                topicRemineService.remineForCorpus(corpus.getId());
            } catch (RuntimeException e) {
                log.warn("topic remine failed for corpus {}: {}", corpus.getName(), e.getMessage());
            }
        });
    }
}
