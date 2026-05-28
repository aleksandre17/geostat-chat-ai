package com.geostat.chat.domain.chat;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;

/**
 * Result of the shared pipeline step in ChatService.
 * Compiler-enforced exhaustiveness via sealed type.
 */
public sealed interface PipelineResult
        permits PipelineResult.Ready, PipelineResult.NeedsClarification {

    record Ready(
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            List<RetrievedChunk> ragChunks) implements PipelineResult {}

    record NeedsClarification(
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<RetrievedChunk> ragChunks,
            AiChatResult result) implements PipelineResult {}
}
