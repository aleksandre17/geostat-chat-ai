package com.geostat.ingestion.crawl.fetch;

import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrawlFetchInfrastructure implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CrawlFetchInfrastructure.class);

    public static final String USER_AGENT = "geostat-ingestion/0.1 (+https://www.geostat.ge)";

    private final PageFetcher pageFetcher;
    private final RobotstxtServer robotsServer;

    private CrawlFetchInfrastructure(PageFetcher pageFetcher, RobotstxtServer robotsServer) {
        this.pageFetcher = pageFetcher;
        this.robotsServer = robotsServer;
    }

    public static CrawlFetchInfrastructure create() {
        try {
            Path storage = Files.createTempDirectory("geostat-crawler4j-");
            CrawlConfig config = new CrawlConfig();
            config.setCrawlStorageFolder(storage.toString());
            config.setUserAgentString(USER_AGENT);
            config.setIncludeHttpsPages(true);
            config.setPolitenessDelay(0);
            config.setMaxDownloadSize(10 * 1024 * 1024);

            PageFetcher pageFetcher = new PageFetcher(config);
            RobotstxtConfig robotsConfig = new RobotstxtConfig();
            robotsConfig.setEnabled(true);
            RobotstxtServer robotsServer = new RobotstxtServer(robotsConfig, pageFetcher);
            return new CrawlFetchInfrastructure(pageFetcher, robotsServer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize crawler4j fetch infrastructure", e);
        }
    }

    public PageFetcher pageFetcher() {
        return pageFetcher;
    }

    public RobotstxtServer robotsServer() {
        return robotsServer;
    }

    @Override
    public void close() {
        try {
            pageFetcher.shutDown();
        } catch (Exception e) {
            log.warn("crawler4j shutdown failed: {}", e.getMessage());
        }
    }
}
