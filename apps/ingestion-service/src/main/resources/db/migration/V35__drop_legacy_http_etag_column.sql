-- V24 introduced canonical etag_http; V5 legacy http_etag is redundant.
ALTER TABLE ingestion.document DROP COLUMN IF EXISTS http_etag;
