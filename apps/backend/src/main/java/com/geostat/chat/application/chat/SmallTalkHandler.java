package com.geostat.chat.application.chat;

import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.PortalListConfig;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.SmallTalkGroup;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.SmallTalkRoot;
import org.springframework.stereotype.Component;

/**
 * Handles small-talk detection and portal-list query detection.
 * All patterns and responses are driven by {@code catalog/small-talk.yaml}.
 * Returns {@code null} when the message is not small talk.
 */
@Component
public class SmallTalkHandler {

    private final SmallTalkCatalogLoader loader;
    private final KeywordMatcher keywordMatcher;

    public SmallTalkHandler(SmallTalkCatalogLoader loader, KeywordMatcher keywordMatcher) {
        this.loader         = loader;
        this.keywordMatcher = keywordMatcher;
    }

    public String handle(String message, boolean isGeorgian) {
        String lower = message.toLowerCase();
        for (SmallTalkGroup group : loader.get().groups()) {
            if (group.maxLength() > 0 && message.length() > group.maxLength()) continue;
            if (keywordMatcher.containsAny(lower, group.patterns())) {
                return isGeorgian ? group.response().ka() : group.response().en();
            }
        }
        return null;
    }

    public String clarificationRequest(boolean isGeorgian) {
        SmallTalkRoot root = loader.get();
        return isGeorgian ? root.clarification().ka() : root.clarification().en();
    }

    public boolean isPortalListQuery(String lowerQuery) {
        PortalListConfig cfg = loader.get().portalList();
        if (keywordMatcher.containsAny(lowerQuery, cfg.excludeKeywords())) return false;
        boolean hasPortal      = keywordMatcher.containsAny(lowerQuery, cfg.portalKeywords());
        boolean hasCalc        = keywordMatcher.containsAny(lowerQuery, cfg.calculatorKeywords());
        boolean isListReq      = keywordMatcher.containsAny(lowerQuery, cfg.listRequestKeywords());
        boolean isShortGeneric = (hasPortal || hasCalc) && lowerQuery.length() < cfg.maxGenericLength();
        return isListReq || isShortGeneric;
    }
}
