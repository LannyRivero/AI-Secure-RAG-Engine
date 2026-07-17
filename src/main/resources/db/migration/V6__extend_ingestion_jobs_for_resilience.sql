-- V6: Retries, backoff metadata, and dead-letter support for ingestion jobs

ALTER TABLE document_ingestions
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN last_error_at TIMESTAMPTZ,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

UPDATE document_ingestions
SET next_attempt_at = requested_at
WHERE next_attempt_at IS NULL;

CREATE INDEX idx_document_ingestions_status_next_attempt
    ON document_ingestions (status, next_attempt_at, requested_at);
