-- V34 — Remove dead 'fetchMode' key from corpus policy JSON.
-- renderMode is exclusively owned by ops/config/corpus/{name}-parse.yaml;
-- keeping fetchMode in the DB column is misleading dead metadata (audit C-05).
UPDATE ingestion.corpus
SET policy = policy - 'fetchMode'
WHERE policy ? 'fetchMode';
