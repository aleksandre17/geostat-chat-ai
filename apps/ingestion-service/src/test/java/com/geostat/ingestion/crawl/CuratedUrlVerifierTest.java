package com.geostat.ingestion.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.SubdomainMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CuratedUrlVerifierTest {

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private CorpusConfigurationLoader configurationLoader;

    private HttpServer server;
    private String baseUrl;
    private CuratedUrlVerifier verifier;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/healthy", exchange -> respond(exchange, 200));
        server.createContext("/stale", exchange -> respond(exchange, 404));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        verifier = new CuratedUrlVerifier(corpusRepository, configurationLoader);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void verify_logsWarn_forStaleUrl() {
        CorpusPolicyV2 policy = policyWith(baseUrl + "/stale");
        CuratedUrlVerifier.VerificationReport report = verifier.verifyCuratedUrls(policy);
        assertThat(report.hasStale()).isTrue();
        assertThat(report.staleUrls()).hasSize(1);
        assertThat(report.staleUrls().get(0).httpStatus()).isEqualTo(404);
    }

    @Test
    void verify_returnsEmpty_whenAllHealthy() {
        CorpusPolicyV2 policy = policyWith(baseUrl + "/healthy");
        CuratedUrlVerifier.VerificationReport report = verifier.verifyCuratedUrls(policy);
        assertThat(report.hasStale()).isFalse();
    }

    @Test
    void verifyOnStartup_checksAllCorpora() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setName("geostat-portal");
        when(corpusRepository.findAll()).thenReturn(List.of(corpus));
        when(configurationLoader.policyFor("geostat-portal")).thenReturn(policyWith(baseUrl + "/healthy"));
        verifier.verifyOnStartup();
    }

    private static CorpusPolicyV2 policyWith(String url) {
        return new CorpusPolicyV2(
                "geostat-portal",
                List.of(),
                List.of(url),
                List.of(),
                SubdomainMode.all,
                List.of(),
                List.of(),
                List.of());
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        try (OutputStream ignored = exchange.getResponseBody()) {
            // HEAD — no body
        }
    }
}
