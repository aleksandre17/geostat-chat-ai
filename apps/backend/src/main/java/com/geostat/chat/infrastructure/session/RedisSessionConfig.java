package com.geostat.chat.infrastructure.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "geostat.session.store", havingValue = "redis")
public class RedisSessionConfig {

    @Bean
    RedisConnectionFactory chatRedisConnectionFactory(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    StringRedisTemplate chatSessionRedisTemplate(RedisConnectionFactory chatRedisConnectionFactory) {
        return new StringRedisTemplate(chatRedisConnectionFactory);
    }
}
