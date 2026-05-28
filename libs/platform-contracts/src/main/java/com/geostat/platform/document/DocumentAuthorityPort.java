package com.geostat.platform.document;

import java.util.UUID;

/**
 * Write port for the authority scoring lifecycle phase.
 * Only the authority deriver should inject this port.
 */
public interface DocumentAuthorityPort {

    /** Update authority_score column (PageRank × freshness). */
    void updateAuthorityScore(UUID documentId, double authorityScore);
}
