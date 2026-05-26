package com.geostat.platform.parse;

import java.util.List;

/**
 * Locale-bucketed boilerplate markers.
 *
 * <p>Match semantics (strict, to avoid stripping legitimate body content):
 *
 * <ul>
 *   <li>{@code ka}/{@code en}/{@code shared} — paragraph dropped only when it
 *       <em>starts with</em> the marker. Use for short UI labels (buttons, link
 *       text) where containment-anywhere would corrupt real content.
 *   <li>{@code containsKa}/{@code containsEn}/{@code containsShared} — paragraph
 *       dropped when the marker appears anywhere. Use for long descriptive
 *       phrases that can be embedded mid-sentence (footer composites,
 *       accessibility notices).
 * </ul>
 */
public record BoilerplateMarkers(
        List<String> ka,
        List<String> en,
        List<String> shared,
        List<String> containsKa,
        List<String> containsEn,
        List<String> containsShared) {

    public BoilerplateMarkers {
        ka = ka == null ? List.of() : List.copyOf(ka);
        en = en == null ? List.of() : List.copyOf(en);
        shared = shared == null ? List.of() : List.copyOf(shared);
        containsKa = containsKa == null ? List.of() : List.copyOf(containsKa);
        containsEn = containsEn == null ? List.of() : List.copyOf(containsEn);
        containsShared = containsShared == null ? List.of() : List.copyOf(containsShared);
    }

    /** Backward-compatible constructor — startsWith-only markers, no contains buckets. */
    public BoilerplateMarkers(List<String> ka, List<String> en, List<String> shared) {
        this(ka, en, shared, List.of(), List.of(), List.of());
    }
}
