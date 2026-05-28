package com.geostat.platform.parse;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Post-Jsoup text sanitization.
 *
 * <p>Applied AFTER Jsoup's element.text() extraction, BEFORE boilerplate filtering.
 * Removes Unicode artifacts that Jsoup's .text() does not strip:
 *
 * <ul>
 *   <li>Icon font characters (Unicode Private Use Area)
 *   <li>Zero-width and invisible characters
 *   <li>Bidirectional control marks
 *   <li>Redundant whitespace
 * </ul>
 *
 * <p>Does NOT alter Georgian (U+10A0–U+10FF) or any standard Unicode range.
 */
public final class TextSanitizer {

    private TextSanitizer() {}

    // Unicode Private Use Area — web font icons (FontAwesome, custom glyph fonts)
    private static final Pattern PUA_CHARS =
            Pattern.compile("[\uE000-\uF8FF\\x{F0000}-\\x{FFFFF}]");

    // Zero-width and invisible formatting characters
    private static final Pattern INVISIBLE_CHARS =
            Pattern.compile("[\u200B\u200C\u200D\u00AD\uFEFF]");

    // Bidirectional control marks
    private static final Pattern BIDI_MARKS =
            Pattern.compile("[\u200E\u200F\u202A-\u202E\u2066-\u2069]");

    // Collapse multiple spaces/newlines after removal
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \t]{2,}");

    /**
     * Full sanitization pipeline for extracted text blocks.
     *
     * <p>Order:
     *
     * <ol>
     *   <li>NFKC Unicode normalization (converts NBSP \u00A0 to space, etc.)
     *   <li>Strip PUA icon font characters
     *   <li>Strip invisible / zero-width chars
     *   <li>Strip bidi marks
     *   <li>Collapse multi-space (but preserve \n\n paragraph separators)
     *   <li>Trim
     * </ol>
     */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = PUA_CHARS.matcher(s).replaceAll("");
        s = INVISIBLE_CHARS.matcher(s).replaceAll("");
        s = BIDI_MARKS.matcher(s).replaceAll("");
        s = MULTI_SPACE.matcher(s).replaceAll(" ");
        return s.strip();
    }

    /**
     * Sanitize a single extracted text block (no newline preservation needed). Used inside
     * extractBody() per-block.
     */
    public static String sanitizeBlock(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = PUA_CHARS.matcher(s).replaceAll("");
        s = INVISIBLE_CHARS.matcher(s).replaceAll("");
        s = BIDI_MARKS.matcher(s).replaceAll("");
        return s.replaceAll("\\s+", " ").strip();
    }

    /** True if the text is non-null, non-blank after sanitization. */
    public static boolean hasContent(String text) {
        return !sanitizeBlock(text).isBlank();
    }
}
