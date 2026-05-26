package com.geostat.platform.enrichment;

import java.util.List;

/** Port — Gemini few-shot entity extraction (RAG-U01c). */
public interface EntityDeriver {

    List<Entity> deriveEntities(DocumentContext document);
}
