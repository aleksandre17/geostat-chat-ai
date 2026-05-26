package com.geostat.chat.domain.catalog;

import java.util.List;

/** Resolves human-readable topic labels for prompts and chat responses (yaml enum vs derived clusters). */
public interface CatalogTopicLabelResolver {

    record Labels(String primary, List<String> all) {}

    Labels resolve(List<Topic> detectedTopics, String query, String language, boolean georgian);
}
