package com.geostat.chat.domain.catalog;

/** One row from Layer 3 catalog materialized views (RAG-U02). */
public record DerivedCatalogLink(
        String url, String title, String summary, String pageKind, double authorityScore) {}
