package com.geostat.ingestion.api;

import com.geostat.platform.crawl.CrawlJob;
import com.geostat.platform.crawl.CrawlOrchestrator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/admin/crawl")
public class AdminCrawlController {

    private final CrawlOrchestrator orchestrator;

    public AdminCrawlController(CrawlOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/all")
    public Map<String, Object> crawlAll() {
        List<CrawlJob> jobs = orchestrator.discoverJobs();
        orchestrator.executeAll(jobs);
        return Map.of(
                "discovered", jobs.size(),
                "corpora", jobs.stream().map(CrawlJob::corpusName).toList());
    }
}
