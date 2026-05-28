-- V30: pg_trgm extension + GIN index for fast keyword cluster matching

-- Enable pg_trgm for trigram-based ILIKE index scans
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram index on mv_topic_keywords.keyword
-- Enables: WHERE keyword ILIKE '%term%' to use index instead of seq scan
CREATE INDEX IF NOT EXISTS idx_mv_topic_keywords_keyword_trgm
  ON ingestion.mv_topic_keywords
  USING gin (keyword gin_trgm_ops);

-- Index on curation_override.target for fast lookup
CREATE INDEX IF NOT EXISTS idx_curation_override_target
  ON ingestion.curation_override (target);
