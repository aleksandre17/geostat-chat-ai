-- V27: encoding issue flag for character encoding mismatch detection
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS encoding_issue BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ingestion.document.encoding_issue IS
  'True if EncodingMismatchDetector identified likely charset corruption on a /ka/ URL.
   Document will be REJECTED by LanguageConsistencyValidator.
   Operator action: check server Content-Type headers for this URL.';
