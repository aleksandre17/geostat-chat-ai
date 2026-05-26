package com.geostat.ingestion.config;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@Profile("db")
@EnableAsync
@EnableConfigurationProperties(EnrichmentProperties.class)
public class EnrichmentConfiguration {}
