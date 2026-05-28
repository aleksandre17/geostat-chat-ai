package com.geostat.ingestion.crawl.job;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CrawlProperties.class)
class CrawlConfiguration {}
