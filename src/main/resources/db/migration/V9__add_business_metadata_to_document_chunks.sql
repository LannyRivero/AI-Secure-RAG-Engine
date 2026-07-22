-- V9: Persist structured business metadata on indexed chunks for faceted retrieval

ALTER TABLE document_chunks
    ADD COLUMN metadata_document_type VARCHAR(100),
    ADD COLUMN metadata_document_date DATE,
    ADD COLUMN metadata_source VARCHAR(255),
    ADD COLUMN metadata_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    ADD COLUMN metadata_owner VARCHAR(100),
    ADD COLUMN metadata_classification VARCHAR(100);

CREATE INDEX idx_chunks_tenant_document_type
    ON document_chunks (tenant_id, metadata_document_type);

CREATE INDEX idx_chunks_tenant_document_date
    ON document_chunks (tenant_id, metadata_document_date);

CREATE INDEX idx_chunks_tenant_source
    ON document_chunks (tenant_id, metadata_source);

CREATE INDEX idx_chunks_tenant_owner
    ON document_chunks (tenant_id, metadata_owner);

CREATE INDEX idx_chunks_tenant_classification
    ON document_chunks (tenant_id, metadata_classification);

CREATE INDEX idx_chunks_metadata_tags
    ON document_chunks USING gin (metadata_tags);
