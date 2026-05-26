package com.geostat.ingestion.parse.profile;

import com.geostat.platform.parse.QualityThresholds;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ParseProperties.class)
public class ParseConfiguration {

    @Bean
    QualityThresholds qualityThresholds() {
        return QualityThresholds.p0Defaults();
    }
}
