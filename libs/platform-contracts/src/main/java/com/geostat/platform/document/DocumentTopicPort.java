package com.geostat.platform.document;

import java.util.UUID;

/**
 * Write port for the topic clustering lifecycle phase.
 * Only topic mining services should inject this port.
 */
public interface DocumentTopicPort {

    /** Update topic_cluster_id column. */
    void updateTopicCluster(UUID documentId, UUID topicClusterId);
}
