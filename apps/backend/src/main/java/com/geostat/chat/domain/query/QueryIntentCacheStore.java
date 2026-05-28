package com.geostat.chat.domain.query;

import java.util.Optional;

/** Port: durable intent classification cache (RAG-U14). */
public interface QueryIntentCacheStore {

    Optional<QueryIntentKind> get(String normalizedQuery, String locale);

    void put(String normalizedQuery, String locale, QueryIntentKind intent);
}
