package com.geostat.chat.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.application.chat.QueryRouter;
import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentCacheStore;
import com.geostat.chat.infrastructure.query.CachingIntentClassifier;
import com.geostat.chat.infrastructure.query.HeuristicIntentClassifier;
import com.geostat.chat.infrastructure.query.RoutingIntentClassifier;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QueryUnderstandingProperties.class)
public class QueryUnderstandingConfiguration {

    @Bean
    QueryRouter queryRouter(KeywordMatcher keywordMatcher) throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/route-keywords.yaml");
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        RouteKeywordsFile file = yaml.readValue(resource.getInputStream(), RouteKeywordsFile.class);
        Map<QueryIntent, List<String>> map = new EnumMap<>(QueryIntent.class);
        if (file.routes() != null) {
            file.routes().forEach((key, values) -> map.put(QueryIntent.valueOf(key), values));
        }
        return new QueryRouter(map, keywordMatcher);
    }

    @Bean
    HeuristicIntentClassifier heuristicIntentClassifier(KeywordMatcher keywordMatcher) throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/intent-keywords.yaml");
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        IntentKeywordsFile file = yaml.readValue(resource.getInputStream(), IntentKeywordsFile.class);
        Map<QueryIntentKind, List<String>> map = new EnumMap<>(QueryIntentKind.class);
        if (file.intents() != null) {
            file.intents().forEach((key, values) -> map.put(QueryIntentKind.valueOf(key), values));
        }
        return new HeuristicIntentClassifier(map, keywordMatcher);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "geostat.chat.query",
            name = "intent-cache-enabled",
            havingValue = "false",
            matchIfMissing = true)
    IntentClassifier defaultIntentClassifier(RoutingIntentClassifier routingIntentClassifier) {
        return routingIntentClassifier;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "geostat.chat.query",
            name = "intent-cache-enabled",
            havingValue = "true",
            matchIfMissing = false)
    IntentClassifier primaryIntentClassifier(
            @Qualifier("routingIntentClassifier") IntentClassifier routingIntentClassifier,
            @Autowired(required = false) QueryIntentCacheStore cacheStore,
            @Value("${geostat.chat.query.intent-cache-ttl-hours:24}") int ttlHours,
            @Value("${geostat.chat.query.intent-cache-max-entries:10000}") int maxEntries) {
        return new CachingIntentClassifier(routingIntentClassifier, cacheStore, ttlHours, maxEntries);
    }

    private record RouteKeywordsFile(Map<String, List<String>> routes) {}

    private record IntentKeywordsFile(Map<String, List<String>> intents) {}
}
