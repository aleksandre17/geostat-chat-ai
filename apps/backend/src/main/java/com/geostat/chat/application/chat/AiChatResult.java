package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;

import java.util.List;

/** Parsed AI JSON body before ChatResponse assembly. */
public record AiChatResult(String intro, List<LinkedExplanation> items) {

    public static AiChatResult emptyIntro(String intro) {
        return new AiChatResult(intro, List.of());
    }

    public static AiChatResult withLinks(String intro, List<LinkCard> links) {
        return new AiChatResult(intro, links.stream().map(l -> new LinkedExplanation(null, l)).toList());
    }
}
