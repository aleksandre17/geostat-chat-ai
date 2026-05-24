package com.geostat.chat.application.telemetry;

/** Feedback command from a completed chat turn. */
public record ChatFeedbackCommand(String turnId, String sessionId, String rating, String comment) {}
