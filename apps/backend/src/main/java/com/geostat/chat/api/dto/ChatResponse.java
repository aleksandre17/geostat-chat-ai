package com.geostat.chat.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import java.util.List;

/** User-facing chat turn (R-01…R-05). Telemetry stays server-side. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        @JsonProperty("intro") String intro,
        @JsonProperty("items") List<LinkedExplanation> items,
        @JsonProperty("language") String language,
        @JsonProperty("primaryTopic") String primaryTopic,
        @JsonProperty("topics") List<String> topics,
        @JsonProperty("topicIcon") String topicIcon,
        @JsonProperty("topicColor") String topicColor,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("turnId") String turnId,
        @JsonProperty("responseType") String responseType,
        @JsonProperty("grounded") boolean grounded,
        @JsonProperty("sourceCount") int sourceCount,
        @JsonProperty("error") ChatErrorDetail error) {}
