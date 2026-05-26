package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentKind;

/** RAG-U07c heuristic classifier — used directly and as Gemini fallback. */
public class HeuristicIntentClassifier implements IntentClassifier {

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        if (message == null || message.isBlank()) {
            return QueryIntentKind.LOOKUP;
        }
        String lower = normalized == null ? message.toLowerCase() : normalized.toLowerCase();
        if (containsAny(lower, "compare", "versus", "vs", "შედარ", " და ")) {
            return QueryIntentKind.COMPARE;
        }
        if (containsAny(
                lower,
                "what is",
                "what does",
                "define",
                "explain",
                "meaning of",
                "რა არის",
                "რას ნიშნავს")) {
            return QueryIntentKind.FACTUAL;
        }
        if (containsAny(lower, "definition", "განმარტება", "meaning")) {
            return QueryIntentKind.DEFINITION;
        }
        if (containsAny(
                lower,
                "latest",
                "recent",
                "2024",
                "2025",
                "ბოლო",
                "ახალი მონაცემ")) {
            return QueryIntentKind.LATEST;
        }
        if (containsAny(
                lower,
                "show me",
                "where can i find",
                "where to find",
                "give me",
                "download",
                "find",
                "open",
                "go to",
                "navigate",
                "portal",
                "მაჩვენე",
                "სად ვნახო",
                "სად არის",
                "პორტალი",
                "გადავიდე",
                "გახსენი")) {
            return QueryIntentKind.NAVIGATION;
        }
        if (containsAny(
                lower,
                "hello",
                "hi",
                "thanks",
                "thank you",
                "გამარჯობა",
                "მადლობა",
                "როგორ ხარ")) {
            return QueryIntentKind.SMALLTALK;
        }
        return QueryIntentKind.LOOKUP;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
