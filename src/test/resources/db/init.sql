CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(50) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536)
);

-- Índice compuesto (tenant + doc) para deletes/queries administrativas
CREATE INDEX IF NOT EXISTS idx_chunks_tenant_doc
    ON document_chunks (tenant_id, document_id);

-- Para retrieval por tenant + distancia
CREATE INDEX IF NOT EXISTS idx_chunks_tenant_id
    ON document_chunks (tenant_id);