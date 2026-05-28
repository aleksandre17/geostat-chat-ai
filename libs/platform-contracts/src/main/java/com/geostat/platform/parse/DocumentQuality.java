package com.geostat.platform.parse;

/**
 * Quality classification of a parsed document.
 *
 * GOOD     — passes all validators; persisted + indexed in Qdrant
 * DEGRADED — minor issues found (auto-fixed where possible); persisted + indexed
 * SKIP     — valid HTML but zero statistical content (e.g. portal landing page);
 *            persisted (fetch_status='parsed') but excluded from MV + Qdrant
 * REJECT   — content too short or structurally invalid; NOT persisted to DB
 */
public enum DocumentQuality {
    GOOD, DEGRADED, SKIP, REJECT
}
