-- V9: Persist structured business metadata on indexed chunks for faceted retrieval

ALTER TABLE document_chunks
    ADD COLUMN metadata_document_type VARCHAR(100),
    ADD COLUMN metadata_document_date DATE,
    ADD COLUMN metadata_source VARCHAR(255),
    ADD COLUMN metadata_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    ADD COLUMN metadata_owner VARCHAR(100),
    ADD COLUMN metadata_classification VARCHAR(100);
