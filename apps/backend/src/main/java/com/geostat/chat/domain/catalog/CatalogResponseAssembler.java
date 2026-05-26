package com.geostat.chat.domain.catalog;

import java.util.List;

/** Assembles derived/yaml catalog labels + link cards for one chat turn (single derived cluster match). */
public interface CatalogResponseAssembler {

    record Bundle(CatalogTopicLabelResolver.Labels topicLabels, List<LinkCard> links) {}

    Bundle assemble(List<Topic> detectedTopics, String query, String language, boolean georgian);

    List<LinkCard> buildPortalLinks(boolean georgian);
}
