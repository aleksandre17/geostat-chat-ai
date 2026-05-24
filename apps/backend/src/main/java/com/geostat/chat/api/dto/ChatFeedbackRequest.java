package com.geostat.chat.api.dto;

/** User rating for a chat turn (B-19). */
public record ChatFeedbackRequest(
        String turnId,
        String sessionId,
        String rating,
        String comment
) {}
