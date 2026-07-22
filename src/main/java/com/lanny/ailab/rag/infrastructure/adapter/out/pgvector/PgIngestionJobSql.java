package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

/**
 * Shared SQL fragments for durable ingestion job persistence.
 */
final class PgIngestionJobSql {

    static final String JOB_COLUMNS = """
            tenant_id, document_id, content, metadata_document_type, metadata_document_date, metadata_source, metadata_tags,
            metadata_owner, metadata_classification, source_type, source_uri, status, request_version, chunks_indexed, error_message,
            retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
            next_attempt_at, last_error_at, dead_lettered_at
            """;

    static final String JOB_COLUMNS_WITH_DI_ALIAS = aliasedColumns("di");

    private PgIngestionJobSql() {
    }

    private static String aliasedColumns(String alias) {
        return """
                %s.tenant_id AS tenant_id, %s.document_id AS document_id, %s.content AS content,
                %s.metadata_document_type AS metadata_document_type, %s.metadata_document_date AS metadata_document_date,
                %s.metadata_source AS metadata_source, %s.metadata_tags AS metadata_tags,
                %s.metadata_owner AS metadata_owner, %s.metadata_classification AS metadata_classification,
                %s.source_type AS source_type, %s.source_uri AS source_uri, %s.status AS status,
                %s.request_version AS request_version, %s.chunks_indexed AS chunks_indexed,
                %s.error_message AS error_message, %s.retry_count AS retry_count, %s.max_attempts AS max_attempts,
                %s.requested_at AS requested_at, %s.started_at AS started_at, %s.completed_at AS completed_at,
                %s.updated_at AS updated_at, %s.next_attempt_at AS next_attempt_at,
                %s.last_error_at AS last_error_at, %s.dead_lettered_at AS dead_lettered_at
                """
                .formatted(alias, alias, alias, alias, alias, alias, alias, alias, alias, alias, alias, alias,
                        alias, alias, alias, alias, alias, alias, alias, alias, alias, alias, alias, alias);
    }
}
