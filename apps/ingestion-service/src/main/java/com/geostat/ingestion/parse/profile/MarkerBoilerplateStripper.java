package com.geostat.ingestion.parse.profile;

import com.geostat.platform.parse.BoilerplateMarkers;
import com.geostat.platform.parse.BoilerplateStripper;
import com.geostat.platform.parse.ParseProfile;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Default {@link BoilerplateStripper} adapter.
 *
 * <p>Match policy is intentionally asymmetric to avoid false positives:
 *
 * <ul>
 *   <li>{@link BoilerplateMarkers#ka()}, {@link BoilerplateMarkers#en()},
 *       {@link BoilerplateMarkers#shared()} — {@code startsWith} only (short UI labels).
 *   <li>{@link BoilerplateMarkers#containsKa()}, {@link BoilerplateMarkers#containsEn()},
 *       {@link BoilerplateMarkers#containsShared()} — {@code contains} anywhere
 *       (descriptive boilerplate phrases that may be embedded mid-sentence).
 * </ul>
 */
@Component
public class MarkerBoilerplateStripper implements BoilerplateStripper {

    @Override
    public String stripFromBody(String text, ParseProfile profile) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> paragraphs = splitParagraphs(text);
        if (paragraphs.isEmpty()) {
            return normalizeWhitespace(text);
        }
        int start = 0;
        int end = paragraphs.size();
        if (profile.stripLeading()) {
            while (start < end && isBoilerplateParagraph(paragraphs.get(start), profile)) {
                start++;
            }
        }
        if (profile.stripTrailing()) {
            while (end > start && isBoilerplateParagraph(paragraphs.get(end - 1), profile)) {
                end--;
            }
        }
        List<String> kept = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String paragraph = paragraphs.get(i);
            if (!isBoilerplateParagraph(paragraph, profile)) {
                kept.add(paragraph);
            }
        }
        return String.join(" ", kept).trim();
    }

    @Override
    public boolean isBoilerplateParagraph(String paragraph, ParseProfile profile) {
        if (paragraph == null || paragraph.isBlank()) {
            return true;
        }
        String normalized = normalize(paragraph);
        if (normalized.startsWith("×")) {
            return true;
        }
        BoilerplateMarkers markers = profile.boilerplateMarkers();
        for (String marker : startsWithMarkers(markers)) {
            if (marker.isBlank()) {
                continue;
            }
            if (normalized.startsWith(normalize(marker))) {
                return true;
            }
        }
        for (String marker : containsMarkers(markers)) {
            if (marker.isBlank()) {
                continue;
            }
            if (normalized.contains(normalize(marker))) {
                return true;
            }
        }
        return false;
    }

    static List<String> splitParagraphs(String text) {
        String[] parts = text.split("\\s{2,}|\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty() && !text.isBlank()) {
            paragraphs.add(text.trim());
        }
        return paragraphs;
    }

    private static List<String> startsWithMarkers(BoilerplateMarkers markers) {
        List<String> all = new ArrayList<>(markers.ka().size() + markers.en().size() + markers.shared().size());
        all.addAll(markers.ka());
        all.addAll(markers.en());
        all.addAll(markers.shared());
        return all;
    }

    private static List<String> containsMarkers(BoilerplateMarkers markers) {
        List<String> all = new ArrayList<>(
                markers.containsKa().size() + markers.containsEn().size() + markers.containsShared().size());
        all.addAll(markers.containsKa());
        all.addAll(markers.containsEn());
        all.addAll(markers.containsShared());
        return all;
    }

    private static String normalize(String value) {
        return normalizeWhitespace(Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)).toLowerCase();
    }

    private static String normalizeWhitespace(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }
}
