package Chatbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frontend-facing link card — serialized as JSON in the chat response.
 */
public record LinkCard(
        @JsonProperty("url")     String url,
        @JsonProperty("titleKa") String titleKa,
        @JsonProperty("titleEn") String titleEn,
        @JsonProperty("type")    String type,
        @JsonProperty("icon")    String icon,
        @JsonProperty("emoji")   String emoji,
        @JsonProperty("bgColor") String bgColor
) {}