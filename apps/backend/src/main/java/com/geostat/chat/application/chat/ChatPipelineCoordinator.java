package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.CatalogRagLinkMerger;
import com.geostat.chat.application.retrieval.RetrievalContextService;
import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.ChatContext;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ChatPipelineCoordinator {

    sealed interface EarlyExit permits EarlyExit.SmallTalk, EarlyExit.PortalList {
        record SmallTalk(String message) implements EarlyExit {}
        record PortalList() implements EarlyExit {}
    }

    record RetrievalOutcome(
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            List<RetrievedChunk> ragChunks) {}

    private final TopicDetector topicDetector;
    private final CatalogResponseAssembler catalogResponseAssembler;
    private final RetrievalContextService retrievalContextService;
    private final CatalogRagLinkMerger catalogRagLinkMerger;
    private final SmallTalkHandler smallTalkHandler;
    private final Map<QueryIntent, Integer> maxRagByIntent;

    ChatPipelineCoordinator(
            TopicDetector topicDetector,
            CatalogResponseAssembler catalogResponseAssembler,
            RetrievalContextService retrievalContextService,
            CatalogRagLinkMerger catalogRagLinkMerger,
            SmallTalkHandler smallTalkHandler,
            @Value("${geostat.chat.links.max-rag-by-intent.factual:6}") int maxRagFactual,
            @Value("${geostat.chat.links.max-rag-by-intent.lookup:4}") int maxRagLookup,
            @Value("${geostat.chat.links.max-rag-by-intent.navigate:2}") int maxRagNavigate,
            @Value("${geostat.chat.links.max-rag-by-intent.clarify:1}") int maxRagClarify) {
        this.topicDetector = topicDetector;
        this.catalogResponseAssembler = catalogResponseAssembler;
        this.retrievalContextService = retrievalContextService;
        this.catalogRagLinkMerger = catalogRagLinkMerger;
        this.smallTalkHandler = smallTalkHandler;
        this.maxRagByIntent = Map.of(
                QueryIntent.CONCEPT, maxRagFactual,
                QueryIntent.DATA_REQUEST, maxRagLookup,
                QueryIntent.NAVIGATE, maxRagNavigate,
                QueryIntent.CLARIFY, maxRagClarify);
    }

    EarlyExit checkEarlyExit(ChatContext ctx) {
        String smallTalk = smallTalkHandler.handle(ctx.message(), ctx.isGeorgian());
        if (smallTalk != null) {
            return new EarlyExit.SmallTalk(smallTalk);
        }
        if (smallTalkHandler.isPortalListQuery(ctx.lowerQuery())) {
            return new EarlyExit.PortalList();
        }
        return null;
    }

    RetrievalOutcome buildRetrieval(ChatContext ctx) {
        String topicQuery = ctx.analyzedQuery() != null
                ? ctx.analyzedQuery().retrievalText().toLowerCase()
                : ctx.lowerQuery();
        List<Topic> topics = topicDetector.detect(topicQuery, ctx.message(), ctx.history());
        CatalogResponseAssembler.Bundle catalog =
                catalogResponseAssembler.assemble(topics, ctx.analyzedQuery(), ctx.locale(), ctx.isGeorgian());
        CatalogTopicLabelResolver.Labels topicLabels = catalog.topicLabels();
        List<RetrievedChunk> ragChunks = retrievalContextService.retrieve(ctx.retrievalQuery(), ctx.locale());
        List<LinkCard> links = mergedLinks(catalog.links(), ctx, ragChunks);
        return new RetrievalOutcome(topics, topicLabels, links, ragChunks);
    }

    List<LinkCard> mergedLinks(List<LinkCard> catalogLinks, ChatContext ctx, List<RetrievedChunk> ragChunks) {
        int maxRag = maxRagByIntent.getOrDefault(ctx.intent(), catalogRagLinkMerger.maxRag());
        return catalogRagLinkMerger.merge(catalogLinks, ragChunks, ctx.isGeorgian(), maxRag);
    }
}
