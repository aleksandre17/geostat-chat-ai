package com.geostat.ingestion.config;

import com.geostat.ingestion.crawl.fetch.CrawlFetchInfrastructure;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
public class CrawlFetchConfiguration {

    @Bean(destroyMethod = "close")
    CrawlFetchInfrastructure crawlFetchInfrastructure() {
        return CrawlFetchInfrastructure.create();
    }
}
