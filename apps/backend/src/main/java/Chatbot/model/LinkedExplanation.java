package Chatbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pairs an AI-generated explanation with the link card it describes.
 * explanation may be null for non-AI responses (portals list, fallback, small talk).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkedExplanation(
        @JsonProperty("explanation") String explanation,
        @JsonProperty("link")        LinkCard link
) {}