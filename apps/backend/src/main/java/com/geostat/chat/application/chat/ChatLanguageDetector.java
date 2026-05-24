package com.geostat.chat.application.chat;

import org.springframework.stereotype.Component;

/** Georgian vs English from script ratio + optional UI/API locale hint (RAG-L10). */
@Component
public class ChatLanguageDetector {

    public boolean isGeorgian(String text) {
        return "ka".equalsIgnoreCase(resolveLocale(text, null));
    }

    public String resolveLocale(String text, String localeHint) {
        if (localeHint != null && !localeHint.isBlank()) {
            String hint = localeHint.split("-")[0].toLowerCase();
            if (hint.equals("ka") || hint.equals("en")) {
                if (text == null || text.isBlank()) {
                    return hint;
                }
                long geoChars = countGeorgian(text);
                long latinChars = countLatin(text);
                if (geoChars == 0 && latinChars == 0) {
                    return hint;
                }
                if (hint.equals("en") && latinChars >= geoChars) {
                    return "en";
                }
                if (hint.equals("ka") && geoChars >= latinChars) {
                    return "ka";
                }
            }
        }
        long geoChars = countGeorgian(text);
        long latinChars = countLatin(text);
        if (geoChars == 0 && latinChars == 0) {
            return "ka";
        }
        return geoChars >= latinChars ? "ka" : "en";
    }

    private static long countGeorgian(String text) {
        return text.chars().filter(ch -> ch >= 0x10D0 && ch <= 0x10FF).count();
    }

    private static long countLatin(String text) {
        return text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count();
    }
}
