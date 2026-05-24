package com.geostat.retrieval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "geostat.retrieval.cache", name = "backend", havingValue = "redis")
public class RetrievalRedisConfiguration {

    @Bean
    RedisConnectionFactory retrievalRedisConnectionFactory(
            @Value("${SPRING_DATA_REDIS_HOST:127.0.0.1}") String host,
            @Value("${SPRING_DATA_REDIS_PORT:6379}") int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    StringRedisTemplate retrievalRedisTemplate(RedisConnectionFactory retrievalRedisConnectionFactory) {
        return new StringRedisTemplate(retrievalRedisConnectionFactory);
    }
}
