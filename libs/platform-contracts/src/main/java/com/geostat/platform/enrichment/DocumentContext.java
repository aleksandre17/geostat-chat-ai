package com.geostat.platform.enrichment;

import java.util.UUID;

/** Input for per-document enrichment derivers (RAG-U01). */
public record DocumentContext(
        UUID documentId,
        String url,
        String title,
        String contentText,
        String language,
        String sectionPath) {}
