ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS last_modified_http TEXT,
    ADD COLUMN IF NOT EXISTS etag_http TEXT;

COMMENT ON COLUMN ingestion.document.last_modified_http IS 'HTTP Last-Modified response header value. Used as If-Modified-Since on re-crawl.';
COMMENT ON COLUMN ingestion.document.etag_http IS 'HTTP ETag response header value. Used as If-None-Match on re-crawl. Preferred over Last-Modified.';
