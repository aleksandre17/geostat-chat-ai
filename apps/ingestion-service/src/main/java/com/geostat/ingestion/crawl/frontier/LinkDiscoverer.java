package com.geostat.ingestion.crawl.frontier;

import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class LinkDiscoverer {

    private final UrlFrontierRepository urlFrontierRepository;

    public LinkDiscoverer(UrlFrontierRepository urlFrontierRepository) {
        this.urlFrontierRepository = urlFrontierRepository;
    }

    public List<UrlFrontierEntity> discover(
            UUID crawlRunId, CorpusEntity corpus, UrlFrontierEntity parent, Document html, int maxDepth) {
        if (parent.getDepth() >= maxDepth) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<UrlFrontierEntity> discovered = new ArrayList<>();
        Elements links = html.select("a[href]");
        for (Element link : links) {
            String abs = link.absUrl("href");
            if (abs.isBlank() || !seen.add(abs)) {
                continue;
            }
            URI uri;
            try {
                uri = URI.create(abs);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                continue;
            }
            if (!CorpusPolicy.isHostAllowed(corpus, uri.getHost())) {
                continue;
            }
            if (!CorpusPolicy.isUrlAllowed(corpus, abs)) {
                continue;
            }
            String hash = UrlHasher.hash(abs);
            if (urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(crawlRunId, hash)) {
                continue;
            }
            UrlFrontierEntity frontier = new UrlFrontierEntity();
            frontier.setUrl(abs);
            frontier.setUrlHash(hash);
            frontier.setDepth(parent.getDepth() + 1);
            frontier.setParentUrl(parent.getUrl());
            frontier.setStatus(FrontierStatus.queued);
            frontier.setAttemptCount(0);
            discovered.add(frontier);
        }
        return discovered;
    }
}
