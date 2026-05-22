package Chatbot.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS Configuration for Spring Boot 4 / Spring 7
 *
 * Note: In Spring 7, you cannot use allowCredentials=true with allowedOrigins="*"
 * Use allowedOriginPatterns instead.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Use allowedOriginPatterns instead of allowedOrigins for "*"
        config.setAllowedOriginPatterns(List.of("*"));

        // Or for production, specify exact origins:
        // config.setAllowedOrigins(List.of(
        //     "http://localhost:8086",
        //     "http://localhost:3000",
        //     "https://yourdomain.ge"
        // ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}