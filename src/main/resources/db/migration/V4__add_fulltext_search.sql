-- V4: Full-text search support for hybrid retrieval
--
-- Hybrid search combines vector search (semantic) with full-text search (keyword).
-- This solves the problem of exact-term queries where the semantic vector
-- can fail (proper nouns, acronyms, technical terms).
--
-- content_tsv: precomputed tsvector column derived from content.
-- Uses 'spanish' text configuration for stemming by default.
-- If content is multilingual, consider 'simple' as a language-neutral alternative.
--
-- The trigger keeps content_tsv automatically in sync with content.
-- This ensures re-ingest updates the full-text index without app-level changes.
--
-- GIN index: optimal index type for tsvector. Efficiently supports @@ and @> operators.
--
-- ADR: tsvector chosen over ILIKE because:
--   - tsvector applies stemming (searching "resource" finds "resources")
--   - tsvector uses GIN index, ILIKE forces sequential scan
--   - tsvector is the PostgreSQL standard for full-text search

ALTER TABLE document_chunks
    ADD COLUMN content_tsv tsvector;

-- Backfill existing chunks
UPDATE document_chunks
SET content_tsv = to_tsvector('spanish', content);

-- GIN index for efficient full-text search
CREATE INDEX idx_chunks_content_tsv
    ON document_chunks
    USING gin(content_tsv);

-- Trigger to keep content_tsv in sync with content
CREATE OR REPLACE FUNCTION update_content_tsv()
RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('spanish', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_content_tsv
    BEFORE INSERT OR UPDATE OF content
    ON document_chunks
    FOR EACH ROW
    EXECUTE FUNCTION update_content_tsv();