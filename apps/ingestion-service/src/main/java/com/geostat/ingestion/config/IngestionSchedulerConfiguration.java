package com.geostat.ingestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("db")
@EnableScheduling
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionSchedulerConfiguration {}
