-- V7: Persist source metadata for connector-based ingestion

ALTER TABLE document_ingestions
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'RAW_TEXT',
    ADD COLUMN source_uri TEXT;

CREATE INDEX idx_document_ingestions_source_type
    ON document_ingestions (source_type);
