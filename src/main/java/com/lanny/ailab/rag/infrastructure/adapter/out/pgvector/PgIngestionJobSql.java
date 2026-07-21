package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

/**
 * Shared SQL fragments for durable ingestion job persistence.
 */
final class PgIngestionJobSql {

    static final String JOB_COLUMNS = """
            tenant_id, document_id, content, source_type, source_uri, status, request_version, chunks_indexed, error_message,
            retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
            next_attempt_at, last_error_at, dead_lettered_at
            """;

    private PgIngestionJobSql() {
    }
}
