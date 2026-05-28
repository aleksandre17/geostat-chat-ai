package com.geostat.retrieval.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HybridRetrievalProperties.class)
public class HybridRetrievalConfiguration {}
