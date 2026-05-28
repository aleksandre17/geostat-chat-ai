-- V37: convert raw_html_hash from CHAR(64) to VARCHAR(64)
-- Hibernate 6 maps String fields to VARCHAR; CHAR(64)/bpchar fails schema validation.
-- Same pattern as V36 which fixed chunk.content_hash.

ALTER TABLE ingestion.document
  ALTER COLUMN raw_html_hash TYPE VARCHAR(64) USING raw_html_hash::varchar;
