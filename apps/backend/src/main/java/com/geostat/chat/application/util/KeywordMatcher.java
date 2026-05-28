package com.geostat.chat.application.util;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Shared keyword matching utility for SmallTalkHandler, QueryRouter, HeuristicIntentClassifier.
 * Latin keywords use word-boundary matching to prevent "hi" from matching "history".
 * Georgian keywords use substring matching (Georgian chars are outside ASCII \w).
 */
@Component
public class KeywordMatcher {

    public boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String kw : keywords) {
            String kwLower = kw.toLowerCase();
            boolean isLatin = kwLower.chars().allMatch(c -> c < 128);
            if (isLatin) {
                if (Pattern.compile("\\b" + Pattern.quote(kwLower) + "\\b").matcher(text).find()) {
                    return true;
                }
            } else {
                if (text.contains(kwLower)) return true;
            }
        }
        return false;
    }

    /** Varargs overload for inline call sites that don't yet use a list. */
    public boolean containsAny(String text, String... keywords) {
        return containsAny(text, List.of(keywords));
    }
}
