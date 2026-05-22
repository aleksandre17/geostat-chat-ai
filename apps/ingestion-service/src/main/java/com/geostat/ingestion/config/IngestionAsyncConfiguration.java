package com.geostat.ingestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@Profile("db")
@EnableAsync
public class IngestionAsyncConfiguration {
}
