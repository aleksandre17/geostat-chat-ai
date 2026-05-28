package com.geostat.ingestion.parse.frontier;

import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.parse.profile.RoutingUrlFilter;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class FrontierEnqueueService {

    private final CorpusRepository corpusRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final UrlFrontierRepository urlFrontierRepository;
    private final RoutingUrlFilter routingUrlFilter;

    public FrontierEnqueueService(
            CorpusRepository corpusRepository,
            CrawlRunRepository crawlRunRepository,
            UrlFrontierRepository urlFrontierRepository,
            RoutingUrlFilter routingUrlFilter) {
        this.corpusRepository = corpusRepository;
        this.crawlRunRepository = crawlRunRepository;
        this.urlFrontierRepository = urlFrontierRepository;
        this.routingUrlFilter = routingUrlFilter;
    }

    @Transactional
    public FrontierEnqueueResult enqueue(String corpusName, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "urls must not be empty");
        }
        CorpusEntity corpus = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "corpus not found: " + corpusName));

        CrawlRunEntity run = crawlRunRepository
                .findFirstByCorpus_IdOrderByCreatedAtDesc(corpus.getId())
                .orElseGet(() -> createPendingRun(corpus));

        List<String> enqueued = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (String rawUrl : urls) {
            if (rawUrl == null || rawUrl.isBlank()) {
                continue;
            }
            String url = rawUrl.trim();
            if (!routingUrlFilter.shouldEnqueue(url, corpus)) {
                rejected.add(url);
                continue;
            }
            String hash = UrlHasher.hash(url);
            if (urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(run.getId(), hash)) {
                duplicates.add(url);
                continue;
            }
            UrlFrontierEntity frontier = new UrlFrontierEntity();
            frontier.setCrawlRun(run);
            frontier.setUrl(url);
            frontier.setUrlHash(hash);
            frontier.setDepth(0);
            frontier.setParentUrl(null);
            frontier.setStatus(FrontierStatus.queued);
            frontier.setAttemptCount(0);
            urlFrontierRepository.save(frontier);
            enqueued.add(url);
        }

        if (enqueued.isEmpty() && !duplicates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "all URLs already queued for crawl run " + run.getId());
        }

        return new FrontierEnqueueResult(corpusName, run.getId(), enqueued, duplicates, rejected);
    }

    private CrawlRunEntity createPendingRun(CorpusEntity corpus) {
        CrawlRunEntity run = new CrawlRunEntity();
        run.setCorpus(corpus);
        run.setTriggeredBy("frontier-enqueue");
        run.setStatus(CrawlRunStatus.pending);
        return crawlRunRepository.save(run);
    }

    public record FrontierEnqueueResult(
            String corpusName,
            UUID crawlRunId,
            List<String> enqueued,
            List<String> duplicates,
            List<String> rejected) {}
}
