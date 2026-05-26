package com.geostat.ingestion.curation;

import java.util.Locale;
import java.util.Set;

public enum CurationAction {
    BOOST("boost"),
    DEMOTE("demote"),
    EXCLUDE("exclude"),
    PIN_AS_PORTAL("pin_as_portal"),
    RENAME_TOPIC("rename_topic");

    private static final Set<String> ALLOWED = Set.of(
            BOOST.value, DEMOTE.value, EXCLUDE.value, PIN_AS_PORTAL.value, RENAME_TOPIC.value);

    private final String value;

    CurationAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CurationAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        for (CurationAction action : values()) {
            if (action.value.equals(normalized)) {
                return action;
            }
        }
        throw new IllegalArgumentException("unsupported curation action: " + raw);
    }

    public static boolean isAllowed(String raw) {
        return raw != null && ALLOWED.contains(raw.strip().toLowerCase(Locale.ROOT));
    }
}
