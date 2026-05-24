package com.geostat.chat.application.chat;

import com.geostat.chat.domain.chat.QueryIntent;
import org.springframework.stereotype.Component;

@Component
public class QueryRouter {

    public QueryIntent route(String message, String lowerQuery) {
        if (message == null || message.isBlank()) {
            return QueryIntent.CLARIFY;
        }
        if (containsAny(lowerQuery, "what is", "what does", "define", "explain", "meaning of",
                "რა არის", "რას ნიშნავს", "განმარტება")) {
            return QueryIntent.CONCEPT;
        }
        if (containsAny(lowerQuery, "show me", "where can i find", "give me", "download", "find",
                "მაჩვენე", "სად ვნახო", "მომეცი", "ჩამოტვირთ", "open", "go to", "navigate",
                "გადავიდე", "გახსენი")) {
            return QueryIntent.DATA_REQUEST;
        }
        if (containsAny(lowerQuery, "portal", "calculator", "tool", "statistics page", "website",
                "პორტალი", "კალკულატორი", "ინსტრუმენტი", "საიტი", "გვერდი")) {
            return QueryIntent.NAVIGATE;
        }
        // RAG-first default: factual/statistical queries use corpus context, not catalog-only navigation
        return QueryIntent.CONCEPT;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
