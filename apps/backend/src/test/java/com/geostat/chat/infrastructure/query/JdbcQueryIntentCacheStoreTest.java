package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.query.QueryIntentKind;
import org.junit.jupiter.api.Test;

class JdbcQueryIntentCacheStoreTest {

    @Test
    void everyIntentKind_roundTripsThroughStorageFormat() {
        for (QueryIntentKind kind : QueryIntentKind.values()) {
            String stored = JdbcQueryIntentCacheStore.toStoredIntent(kind);
            assertThat(JdbcQueryIntentCacheStore.fromStoredIntent(stored))
                    .as("round-trip for %s stored as %s", kind, stored)
                    .contains(kind);
        }
    }

    @Test
    void statistical_doesNotAliasToFactual() {
        assertThat(JdbcQueryIntentCacheStore.toStoredIntent(QueryIntentKind.STATISTICAL))
                .isEqualTo("statistical");
        assertThat(JdbcQueryIntentCacheStore.fromStoredIntent("statistical"))
                .contains(QueryIntentKind.STATISTICAL);
    }
}
