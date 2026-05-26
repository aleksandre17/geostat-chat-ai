package com.geostat.chat.domain.query;

import com.geostat.platform.enrichment.Entity;
import java.util.List;

/** RAG-U07d — extract structured entities from user query (reuse U01c Entity model). */
public interface QueryEntityExtractor {

    List<Entity> extract(String query, String normalized, String locale);
}
