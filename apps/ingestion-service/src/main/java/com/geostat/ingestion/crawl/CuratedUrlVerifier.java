package com.geostat.ingestion.crawl;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.parse.CorpusPolicyV2;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Startup staleness check for YAML curatedUrls — alerts only, non-blocking. */
@Component
@Profile("db")
public class CuratedUrlVerifier {

    private static final Logger log = LoggerFactory.getLogger(CuratedUrlVerifier.class);
    private static final Duration HEAD_TIMEOUT = Duration.ofMillis(5_000);
    private static final String USER_AGENT = "GeostatBot-verify/1.0";

    private final CorpusRepository corpusRepository;
    private final CorpusConfigurationLoader configurationLoader;
    private final HttpClient httpClient;

    public CuratedUrlVerifier(CorpusRepository corpusRepository, CorpusConfigurationLoader configurationLoader) {
        this.corpusRepository = corpusRepository;
        this.configurationLoader = configurationLoader;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HEAD_TIMEOUT)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void verifyOnStartup() {
        for (CorpusEntity corpus : corpusRepository.findAll()) {
            CorpusPolicyV2 policy = configurationLoader.policyFor(corpus.getName());
            if (policy.curatedUrls().isEmpty()) {
                continue;
            }
            log.info(
                    "[curated-url-verify] corpus={} checking {} URLs",
                    policy.corpus(),
                    policy.curatedUrls().size());
            verifyCuratedUrls(policy);
        }
    }

    public VerificationReport verifyCuratedUrls(CorpusPolicyV2 policy) {
        List<StaleUrl> stale = new ArrayList<>();
        for (String url : policy.curatedUrls()) {
            int status = headStatus(url);
            if (status >= 400) {
                stale.add(new StaleUrl(url, status));
                log.warn("[curated-url-verify] STALE corpus={} url={} status={}", policy.corpus(), url, status);
            } else if (status < 0) {
                stale.add(new StaleUrl(url, status));
                log.warn("[curated-url-verify] UNREACHABLE corpus={} url={}", policy.corpus(), url);
            }
        }
        if (stale.isEmpty()) {
            log.info(
                    "[curated-url-verify] corpus={} — all {} URLs healthy",
                    policy.corpus(),
                    policy.curatedUrls().size());
        } else {
            log.error(
                    "[curated-url-verify] corpus={} — {} STALE URLs found. "
                            + "Update ops/config/corpus/{}-policy.yaml curatedUrls.",
                    policy.corpus(),
                    stale.size(),
                    policy.corpus());
        }
        return new VerificationReport(policy.corpus(), stale);
    }

    private int headStatus(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HEAD_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            log.debug("[curated-url-verify] HEAD failed url={} error={}", url, e.getMessage());
            return -1;
        }
    }

    public record StaleUrl(String url, int httpStatus) {}

    public record VerificationReport(String corpus, List<StaleUrl> staleUrls) {
        public boolean hasStale() {
            return !staleUrls.isEmpty();
        }
    }
}
