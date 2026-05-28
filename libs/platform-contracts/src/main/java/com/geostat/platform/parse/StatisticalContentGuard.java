package com.geostat.platform.parse;

import java.util.regex.Pattern;

/**
 * Guards statistical content blocks from boilerplate stripping.
 *
 * <p>Design contract: a text block containing statistical data signals (numbers, percentages,
 * units, years) MUST NOT be stripped by boilerplate markers, regardless of whether it also
 * contains a boilerplate phrase.
 *
 * <p>Rationale: "official statistics of georgia" is a boilerplate footer phrase AND legitimately
 * appears in news article lead paragraphs that contain key statistical figures. The containsEn
 * marker cannot distinguish these. The guard resolves the ambiguity: if numbers are present →
 * keep.
 *
 * <p>This is intentionally a final utility class (no inheritance, no state). All logic is in
 * one place for easy audit.
 */
public final class StatisticalContentGuard {

    private StatisticalContentGuard() {}

    /**
     * Primary statistical signal patterns.
     *
     * <p>Matched against the raw (non-lowercased) text to preserve Georgian script. Uses
     * Unicode-aware character classes.
     *
     * <p>Patterns:
     *
     * <ol>
     *   <li>Percentage: "8.7%", "12%", "0.5%"
     *   <li>Georgian units: "47 მლნ.", "200 ათასი", "3 მილიარდი", "500 ლარი"
     *   <li>Year + Georgian: "2024 წელს", "2025 წელ"
     *   <li>Decimal number: "1.2", "3,450" (locale-aware separators)
     *   <li>Standalone int: any 2+ digit number — "15", "200", "1 200"
     *   <li>English units: "million", "billion", "thousand", "GEL", "USD"
     *   <li>Ordinal/rank: "#1", "1st", "2nd"
     * </ol>
     */
    private static final Pattern STAT_PATTERN = Pattern.compile(
            // 1. percentage
            "\\d+[.,]?\\d*\\s*%" +
            // 2. Georgian numeric units
            "|\\d+[.,]?\\d*\\s*(მლნ|ათასი|მილ|ლარი|ტ\\.?\\s*ც|ათ\\.\\s*ადამ)" +
            // 3. year + Georgian word
            "|\\d{4}\\s*წელ" +
            // 4. decimal number (both . and , as separator)
            "|\\d{1,3}([.,]\\d{3})+" + // thousand-grouped: 1,234 or 1.234
            "|\\d+[.,]\\d+" + // simple decimal: 8.7 or 8,7
            // 5. standalone integer 2+ digits
            "|\\b\\d{2,}\\b" +
            // 6. English units after number
            "|\\d+[.,]?\\d*\\s*(million|billion|thousand|GEL|USD|EUR)" +
            // 7. ordinal / rank
            "|#\\d+|\\d+(st|nd|rd|th)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Returns true if the text block contains statistical data signals.
     *
     * <p>When true: {@link BoilerplateStripper} implementations MUST return {@code false} from
     * {@code isBoilerplateParagraph()} for this block, regardless of marker matches.
     *
     * @param text raw extracted text block (after TextSanitizer, before normalization)
     */
    public static boolean isStatisticalContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return STAT_PATTERN.matcher(text).find();
    }
}
