package com.geostat.chat.api.dto;

public record ChatRequest(String message, String sessionId, String locale) {
    public ChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }
}