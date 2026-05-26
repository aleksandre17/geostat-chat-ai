package com.geostat.chat.domain.catalog;

import java.util.List;

/** Builds ordered catalog link cards — YAML topics or derived MVs (RAG-U02 cutover flag). */
public interface CatalogLinkBuilder {

    List<LinkCard> buildLinks(List<Topic> topics, String query, boolean isGeorgian);

    List<LinkCard> buildPortalLinks(boolean isGeorgian);

    List<LinkCard> buildFallbackLinks(boolean isGeorgian);
}
