package com.geostat.ingestion.crawl.frontier;

import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.crawl.UrlNormalizer;
import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.parse.profile.RoutingUrlFilter;
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
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class LinkDiscoverer {

    private final UrlFrontierRepository urlFrontierRepository;
    private final RoutingUrlFilter routingUrlFilter;

    public LinkDiscoverer(UrlFrontierRepository urlFrontierRepository, RoutingUrlFilter routingUrlFilter) {
        this.urlFrontierRepository = urlFrontierRepository;
        this.routingUrlFilter = routingUrlFilter;
    }

    public List<UrlFrontierEntity> discover(
            UUID crawlRunId, CorpusEntity corpus, UrlFrontierEntity parent, Document html, int maxDepth) {
        if (parent.getDepth() >= maxDepth) {
            return List.of();
        }

        record Candidate(String url, String hash) {}

        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenOnPage = new HashSet<>();
        Elements links = html.select("a[href]");
        for (Element link : links) {
            String abs = link.absUrl("href");
            if (abs.isBlank() || !seenOnPage.add(abs)) {
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
            if (!routingUrlFilter.shouldEnqueue(abs, corpus)) {
                continue;
            }
            String normalized = UrlNormalizer.normalize(abs);
            candidates.add(new Candidate(normalized, UrlHasher.hash(normalized)));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> allHashes =
                candidates.stream().map(Candidate::hash).collect(Collectors.toSet());
        Set<String> alreadyQueued = new HashSet<>(
                urlFrontierRepository.findExistingHashesByCrawlRunAndHashIn(crawlRunId, allHashes));

        List<UrlFrontierEntity> discovered = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (alreadyQueued.contains(candidate.hash())) {
                continue;
            }
            UrlFrontierEntity frontier = new UrlFrontierEntity();
            frontier.setUrl(candidate.url());
            frontier.setUrlHash(candidate.hash());
            frontier.setDepth(parent.getDepth() + 1);
            frontier.setParentUrl(parent.getUrl());
            frontier.setStatus(FrontierStatus.queued);
            frontier.setAttemptCount(0);
            discovered.add(frontier);
        }
        return discovered;
    }
}
