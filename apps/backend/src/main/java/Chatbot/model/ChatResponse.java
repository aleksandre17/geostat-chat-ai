package Chatbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatResponse(
        @JsonProperty("intro")      String intro,
        @JsonProperty("items")      List<LinkedExplanation> items,
        @JsonProperty("language")   String language,
        @JsonProperty("topic")      String topic,
        @JsonProperty("topics")     List<String> topics,
        @JsonProperty("topicIcon")  String topicIcon,
        @JsonProperty("topicEmoji") String topicEmoji,
        @JsonProperty("topicColor") String topicColor,
        @JsonProperty("sessionId")  String sessionId
) {}