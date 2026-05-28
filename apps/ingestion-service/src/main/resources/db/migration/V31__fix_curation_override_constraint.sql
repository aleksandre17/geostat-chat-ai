-- V31: Fix curation_override unique constraints
-- rename_topic is cluster-scoped (target = cluster_id), not URL-scoped

-- Drop the old single constraint that conflates URL and target uniqueness
ALTER TABLE ingestion.curation_override
  DROP CONSTRAINT IF EXISTS uq_override_url_action;

-- URL-based actions (boost, demote, exclude, pin_as_portal) — unique by (url_hash, action)
CREATE UNIQUE INDEX IF NOT EXISTS uq_curation_url_action
  ON ingestion.curation_override (url_hash, action)
  WHERE action IN ('boost', 'demote', 'exclude', 'pin_as_portal');

-- Target-based actions (rename_topic) — unique by (target, action)
CREATE UNIQUE INDEX IF NOT EXISTS uq_curation_target_action
  ON ingestion.curation_override (target, action)
  WHERE action = 'rename_topic';
