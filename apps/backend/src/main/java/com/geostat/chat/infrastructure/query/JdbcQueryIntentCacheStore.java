package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.QueryIntentCacheStore;
import com.geostat.chat.domain.query.QueryIntentKind;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres adapter for {@code chat.query_intent_cache} (RAG-U14).
 * Uses the catalog JDBC pool when {@code geostat.chat.catalog.source=derived}.
 */
@Component
@ConditionalOnProperty(
        prefix = "geostat.chat.query",
        name = "intent-cache-enabled",
        havingValue = "true",
        matchIfMissing = false)
@ConditionalOnBean(name = "catalogJdbcTemplate")
public class JdbcQueryIntentCacheStore implements QueryIntentCacheStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcQueryIntentCacheStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final int ttlHours;

    public JdbcQueryIntentCacheStore(
            JdbcTemplate catalogJdbcTemplate,
            @Value("${geostat.chat.query.intent-cache-ttl-hours:24}") int ttlHours) {
        this.jdbcTemplate = catalogJdbcTemplate;
        this.ttlHours = ttlHours;
    }

    @Override
    public Optional<QueryIntentKind> get(String normalizedQuery, String locale) {
        String queryHash = QueryIntentCacheKeys.hash(normalizedQuery, locale);
        try {
            return jdbcTemplate
                    .query(
                            """
                            SELECT intent
                            FROM chat.query_intent_cache
                            WHERE query_hash = ?
                              AND expires_at > now()
                            """,
                            (rs, rowNum) -> rs.getString("intent"),
                            queryHash)
                    .stream()
                    .findFirst()
                    .flatMap(JdbcQueryIntentCacheStore::fromStoredIntent);
        } catch (DataAccessException e) {
            log.debug("Intent cache read failed for hash {}: {}", queryHash.substring(0, 8), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String normalizedQuery, String locale, QueryIntentKind intent) {
        String queryHash = QueryIntentCacheKeys.hash(normalizedQuery, locale);
        String resolvedLocale = locale == null || locale.isBlank() ? "ka" : locale.toLowerCase(Locale.ROOT);
        String storedIntent = toStoredIntent(intent);
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO chat.query_intent_cache (query_hash, locale, intent, expires_at)
                    VALUES (?, ?, ?, now() + (? || ' hours')::interval)
                    ON CONFLICT (query_hash) DO UPDATE
                    SET locale = EXCLUDED.locale,
                        intent = EXCLUDED.intent,
                        cached_at = now(),
                        expires_at = EXCLUDED.expires_at
                    """,
                    queryHash,
                    resolvedLocale,
                    storedIntent,
                    ttlHours);
        } catch (DataAccessException e) {
            log.debug("Intent cache write failed for hash {}: {}", queryHash.substring(0, 8), e.getMessage());
        }
    }

    static String toStoredIntent(QueryIntentKind intent) {
        return intent.name().toLowerCase(Locale.ROOT);
    }

    static Optional<QueryIntentKind> fromStoredIntent(String storedIntent) {
        if (storedIntent == null || storedIntent.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(QueryIntentKind.valueOf(storedIntent.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
