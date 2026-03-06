-- V1: Schema inicial de UNADA RAG Engine
-- Tabla principal de chunks vectorizados con aislamiento por tenant

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id          UUID         PRIMARY KEY,
    tenant_id   VARCHAR(50)  NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(1536),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Índice compuesto para queries administrativas (delete por tenant+doc)
CREATE INDEX idx_chunks_tenant_doc
    ON document_chunks (tenant_id, document_id);

-- Índice para retrieval semántico filtrado por tenant
CREATE INDEX idx_chunks_tenant_id
    ON document_chunks (tenant_id);