package com.geostat.chat.infrastructure.config;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.infrastructure.query.HeuristicIntentClassifier;
import com.geostat.chat.infrastructure.query.IdentitySpellFixer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QueryUnderstandingProperties.class)
public class QueryUnderstandingConfiguration {

    @Bean
    HeuristicIntentClassifier heuristicIntentClassifier() {
        return new HeuristicIntentClassifier();
    }

    @Bean
    IdentitySpellFixer identitySpellFixer() {
        return new IdentitySpellFixer();
    }
}
