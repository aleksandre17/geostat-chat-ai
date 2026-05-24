-- B-28 baseline: optional raw HTML archive key (S3/MinIO adapter future)
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS raw_archive_key TEXT;
