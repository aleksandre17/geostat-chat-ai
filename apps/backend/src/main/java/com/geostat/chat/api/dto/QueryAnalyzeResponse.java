package com.geostat.chat.api.dto;

import java.util.List;

public record QueryAnalyzeResponse(
        String intent, String normalized, String retrievalText, List<EntityView> entities) {

    public record EntityView(String type, String value, String normalizedForm, double confidence) {}
}
