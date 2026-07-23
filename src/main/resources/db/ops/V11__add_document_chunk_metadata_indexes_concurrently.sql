-- Manual operational script for production rollouts.
--
-- Apply this after schema migrations complete, during a rollout plan that allows
-- non-transactional concurrent index creation on document_chunks.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_tenant_document_type
    ON document_chunks (tenant_id, metadata_document_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_tenant_document_date
    ON document_chunks (tenant_id, metadata_document_date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_tenant_source
    ON document_chunks (tenant_id, metadata_source);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_tenant_owner
    ON document_chunks (tenant_id, metadata_owner);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_tenant_classification
    ON document_chunks (tenant_id, metadata_classification);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunks_metadata_tags
    ON document_chunks USING gin (metadata_tags);
