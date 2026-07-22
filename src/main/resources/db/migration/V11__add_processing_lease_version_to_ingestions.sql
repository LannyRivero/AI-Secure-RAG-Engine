-- V11: Guard stale reclaim attempts with a per-claim lease version

ALTER TABLE document_ingestions
    ADD COLUMN processing_lease_version BIGINT NOT NULL DEFAULT 0;
