package com.geostat.ingestion.config;

import com.geostat.ingestion.catalog.AggregationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("db")
@EnableConfigurationProperties(AggregationProperties.class)
public class AggregationConfiguration {}
