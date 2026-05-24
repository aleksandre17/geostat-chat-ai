-- RAG-L11: locale-aware display descriptions for citation cards (ingestion source of truth)
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS lead_text TEXT,
    ADD COLUMN IF NOT EXISTS meta_description TEXT,
    ADD COLUMN IF NOT EXISTS display_description TEXT;

-- Backfill parsed docs missing display text (best-effort from cleaned body)
UPDATE ingestion.document
SET display_description = LEFT(TRIM(content_text), 240)
WHERE fetch_status = 'parsed'
  AND display_description IS NULL
  AND content_text IS NOT NULL
  AND LENGTH(TRIM(content_text)) >= 40;
