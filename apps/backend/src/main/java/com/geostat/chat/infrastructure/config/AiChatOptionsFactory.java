package com.geostat.chat.infrastructure.config;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** Builds {@link GoogleGenAiChatOptions} for chat, clarification, and classification paths. */
@Component
public class AiChatOptionsFactory {

    private final AiChatProperties properties;

    public AiChatOptionsFactory(AiChatProperties properties) {
        this.properties = properties;
    }

    public ChatOptions mainChat() {
        return jsonOptions(properties.mainTemperature(), GeminiJsonSchemas.MAIN_RESPONSE);
    }

    public ChatOptions clarification() {
        return jsonOptions(properties.clarificationTemperature(), GeminiJsonSchemas.CLARIFICATION_RESPONSE);
    }

    public ChatOptions classification() {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .temperature(properties.classificationTemperature())
                .maxOutputTokens(64)
                .build();
        options.setSafetySettings(GeminiSafetyDefaults.publicAssistant());
        return options;
    }

    private ChatOptions jsonOptions(double temperature, String schema) {
        GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder()
                .temperature(temperature)
                .maxOutputTokens(properties.maxOutputTokens());
        if (properties.jsonResponseMode()) {
            builder.responseMimeType("application/json").responseSchema(schema);
        }
        GoogleGenAiChatOptions options = builder.build();
        options.setSafetySettings(GeminiSafetyDefaults.publicAssistant());
        return options;
    }
}
