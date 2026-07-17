-- V5: Durable ingestion jobs for asynchronous document processing

CREATE TABLE document_ingestions (
    tenant_id       VARCHAR(50)  NOT NULL,
    document_id     VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    request_version BIGINT       NOT NULL DEFAULT 1,
    chunks_indexed  INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, document_id)
);

CREATE INDEX idx_document_ingestions_status_requested
    ON document_ingestions (status, requested_at);
