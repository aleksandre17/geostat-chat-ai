package com.geostat.chat.application.chat;

/** Extracts the intro field value from a partial Gemini JSON stream for SSE display. */
public final class StreamIntroExtractor {

    private StreamIntroExtractor() {}

    /**
     * Returns the current intro text if the buffer contains an opening {@code "intro":"} field.
     * Handles incomplete JSON during streaming.
     */
    public static String extractIntro(String buffer) {
        if (buffer == null || buffer.isBlank()) {
            return "";
        }
        int key = buffer.indexOf("\"intro\"");
        if (key < 0) {
            return "";
        }
        int colon = buffer.indexOf(':', key);
        if (colon < 0) {
            return "";
        }
        int quoteStart = indexOfNonWhitespace(buffer, colon + 1);
        if (quoteStart < 0 || buffer.charAt(quoteStart) != '"') {
            return "";
        }
        StringBuilder intro = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (escaped) {
                intro.append(unescape(c));
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return intro.toString();
            }
            intro.append(c);
        }
        return intro.toString();
    }

    private static int indexOfNonWhitespace(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> c;
        };
    }
}
