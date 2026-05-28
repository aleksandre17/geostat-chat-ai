-- V21: navigation breadcrumb extracted before removeSelectors

ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS nav_breadcrumb TEXT;

COMMENT ON COLUMN ingestion.document.nav_breadcrumb IS
    'Navigation breadcrumb from site nav (e.g. "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა").
     Extracted before removeSelectors. Null if page has no breadcrumb nav.
     Used for: topic cluster assignment, Chat catalog navigation, MV joins.';

CREATE INDEX IF NOT EXISTS idx_document_nav_breadcrumb
    ON ingestion.document USING gin (to_tsvector('simple', COALESCE(nav_breadcrumb, '')));
