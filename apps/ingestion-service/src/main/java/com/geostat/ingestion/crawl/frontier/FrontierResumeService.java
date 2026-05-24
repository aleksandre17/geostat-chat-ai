package com.geostat.ingestion.crawl.frontier;

import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Copies queued frontier URLs between crawl runs (B-26 resume). */
@Component
@Profile("db")
public class FrontierResumeService {

    private static final int COPY_BATCH = 500;

    private final UrlFrontierRepository urlFrontierRepository;

    public FrontierResumeService(UrlFrontierRepository urlFrontierRepository) {
        this.urlFrontierRepository = urlFrontierRepository;
    }

    @Transactional(readOnly = true)
    public long countQueued(UUID crawlRunId) {
        return urlFrontierRepository.countByCrawlRun_IdAndStatus(crawlRunId, FrontierStatus.queued);
    }

    /** Copy queued URLs from source run into target run; returns number copied. */
    @Transactional
    public int copyQueuedFrontier(UUID sourceRunId, CrawlRunEntity targetRun) {
        List<UrlFrontierEntity> queued =
                urlFrontierRepository.findTop500ByCrawlRun_IdAndStatusOrderByDiscoveredAtAsc(
                        sourceRunId, FrontierStatus.queued);
        int copied = 0;
        for (UrlFrontierEntity source : queued) {
            if (urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(targetRun.getId(), source.getUrlHash())) {
                continue;
            }
            UrlFrontierEntity target = new UrlFrontierEntity();
            target.setCrawlRun(targetRun);
            target.setUrl(source.getUrl());
            target.setUrlHash(source.getUrlHash());
            target.setDepth(source.getDepth());
            target.setParentUrl(source.getParentUrl());
            target.setStatus(FrontierStatus.queued);
            target.setAttemptCount(0);
            urlFrontierRepository.save(target);
            copied++;
            if (copied >= COPY_BATCH) {
                break;
            }
        }
        return copied;
    }
}
