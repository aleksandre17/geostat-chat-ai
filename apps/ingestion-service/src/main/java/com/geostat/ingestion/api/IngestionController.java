package com.geostat.ingestion.api;

import com.geostat.ingestion.crawl.job.CrawlJobService;
import com.geostat.platform.contracts.ingestion.IngestionJobRequest;
import com.geostat.platform.contracts.ingestion.IngestionJobStatus;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final CrawlJobService crawlJobService;

    public IngestionController(CrawlJobService crawlJobService) {
        this.crawlJobService = crawlJobService;
    }

    @PostMapping("/jobs")
    public IngestionJobStatus startJob(@RequestBody IngestionJobRequest request) {
        return crawlJobService.startJob(request);
    }

    @GetMapping("/jobs/{jobId}")
    public IngestionJobStatus getJob(@PathVariable UUID jobId) {
        return crawlJobService.getJob(jobId);
    }
}
