package com.geostat.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ai.chat")
public record AiChatProperties(
        double mainTemperature,
        double clarificationTemperature,
        double classificationTemperature,
        int maxHistoryMessages,
        int maxRagContextChars,
        int maxSystemPromptChars,
        int maxOutputTokens,
        int sessionTtlMinutes,
        boolean jsonResponseMode) {

    public AiChatProperties {
        if (mainTemperature < 0) mainTemperature = 0.6;
        if (clarificationTemperature < 0) clarificationTemperature = 0.3;
        if (classificationTemperature < 0) classificationTemperature = 0.0;
        if (maxHistoryMessages <= 0) maxHistoryMessages = 10;
        if (maxRagContextChars <= 0) maxRagContextChars = 12_000;
        if (maxSystemPromptChars <= 0) maxSystemPromptChars = 28_000;
        if (maxOutputTokens <= 0) maxOutputTokens = 2048;
        if (sessionTtlMinutes <= 0) sessionTtlMinutes = 30;
    }
}
