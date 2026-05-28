package com.geostat.ingestion.curation;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Applies score boost overrides from feedback signals.
 * TODO: implement applyScoreBoost() — write to ingestion.curation_override table.
 */
@Service
public class CurationOverrideService {

    public void applyScoreBoost(UUID documentId, UUID corpusId, double scoreDelta) {
        // TODO: INSERT/UPDATE ingestion.curation_override
    }
}
