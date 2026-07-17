package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of durable ingestion job persistence.
 */
@Component
public class PgIngestionJobRepository implements IngestionJobRepositoryPort {

    private static final RowMapper<IngestionJob> JOB_ROW_MAPPER = new IngestionJobRowMapper();

    private final JdbcTemplate jdbcTemplate;
    private final int configuredMaxAttempts;
    private final long staleProcessingTimeoutSeconds;

    public PgIngestionJobRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${app.rag.ingestion.retry.max-attempts:3}") int configuredMaxAttempts,
            @Value("${app.rag.ingestion.processing-timeout-seconds:300}") long staleProcessingTimeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.configuredMaxAttempts = configuredMaxAttempts;
        this.staleProcessingTimeoutSeconds = staleProcessingTimeoutSeconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IngestionJob enqueue(IngestDocumentCommand command) {
        return queryOne("""
                INSERT INTO document_ingestions (
                    tenant_id, document_id, content, status, request_version,
                    chunks_indexed, error_message, max_attempts, requested_at, started_at, completed_at, updated_at
                )
                VALUES (?, ?, ?, 'PENDING', 1, 0, NULL, ?, now(), NULL, NULL, now())
                ON CONFLICT (tenant_id, document_id)
                DO UPDATE SET
                    content = EXCLUDED.content,
                    status = 'PENDING',
                    request_version = document_ingestions.request_version + 1,
                    chunks_indexed = 0,
                    error_message = NULL,
                    requested_at = now(),
                    started_at = NULL,
                    completed_at = NULL,
                    retry_count = 0,
                    max_attempts = EXCLUDED.max_attempts,
                    next_attempt_at = now(),
                    last_error_at = NULL,
                    dead_lettered_at = NULL,
                    updated_at = now()
                RETURNING tenant_id, document_id, content, status, request_version, chunks_indexed, error_message,
                          retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
                          next_attempt_at, last_error_at, dead_lettered_at
                """,
                command.tenantId().value(),
                command.documentId(),
                command.content(),
                configuredMaxAttempts).orElseThrow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IngestionJob> findByTenantAndDocument(TenantId tenantId, String documentId) {
        return queryOne("""
                SELECT tenant_id, document_id, content, status, request_version, chunks_indexed, error_message,
                       retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
                       next_attempt_at, last_error_at, dead_lettered_at
                FROM document_ingestions
                WHERE tenant_id = ? AND document_id = ?
                """,
                tenantId.value(),
                documentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IngestionJob> claimNextPending() {
        return queryOne(
                """
                        WITH next_job AS (
                            SELECT tenant_id, document_id
                            FROM document_ingestions
                            WHERE (status = 'PENDING' AND next_attempt_at <= now())
                               OR (status = 'PROCESSING' AND started_at IS NOT NULL
                                   AND started_at <= now() - make_interval(secs => ?))
                            ORDER BY COALESCE(started_at, requested_at)
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        UPDATE document_ingestions di
                        SET status = 'PROCESSING',
                            started_at = now(),
                            updated_at = now(),
                            error_message = NULL
                        FROM next_job
                        WHERE di.tenant_id = next_job.tenant_id
                          AND di.document_id = next_job.document_id
                        RETURNING di.tenant_id, di.document_id, di.content, di.status, di.request_version, di.chunks_indexed, di.error_message,
                                  di.retry_count, di.max_attempts, di.requested_at, di.started_at, di.completed_at, di.updated_at,
                                  di.next_attempt_at, di.last_error_at, di.dead_lettered_at
                        """,
                staleProcessingTimeoutSeconds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IngestionJob> findByTenantAndDocumentForUpdate(TenantId tenantId, String documentId) {
        return queryOne("""
                SELECT tenant_id, document_id, content, status, request_version, chunks_indexed, error_message,
                       retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
                       next_attempt_at, last_error_at, dead_lettered_at
                FROM document_ingestions
                WHERE tenant_id = ? AND document_id = ?
                FOR UPDATE
                """,
                tenantId.value(),
                documentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markCompleted(TenantId tenantId, String documentId, long requestVersion, int chunksIndexed) {
        return jdbcTemplate.update("""
                UPDATE document_ingestions
                SET status = 'COMPLETED',
                    chunks_indexed = ?,
                    error_message = NULL,
                    completed_at = now(),
                    next_attempt_at = now(),
                    last_error_at = NULL,
                    dead_lettered_at = NULL,
                    updated_at = now()
                WHERE tenant_id = ? AND document_id = ? AND request_version = ? AND status = 'PROCESSING'
                """,
                chunksIndexed,
                tenantId.value(),
                documentId,
                requestVersion) == 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IngestionJob> markFailed(TenantId tenantId, String documentId, long requestVersion,
            String errorMessage, long backoffSeconds) {
        return queryOne("""
                UPDATE document_ingestions
                SET status = CASE
                        WHEN retry_count + 1 >= max_attempts THEN 'DEAD_LETTER'
                        ELSE 'PENDING'
                    END,
                    error_message = ?,
                    retry_count = retry_count + 1,
                    next_attempt_at = CASE
                        WHEN retry_count + 1 >= max_attempts THEN now()
                        ELSE now() + make_interval(secs => ?)
                    END,
                    last_error_at = now(),
                    dead_lettered_at = CASE
                        WHEN retry_count + 1 >= max_attempts THEN now()
                        ELSE NULL
                    END,
                    started_at = NULL,
                    updated_at = now()
                WHERE tenant_id = ? AND document_id = ? AND request_version = ? AND status = 'PROCESSING'
                RETURNING tenant_id, document_id, content, status, request_version, chunks_indexed, error_message,
                          retry_count, max_attempts, requested_at, started_at, completed_at, updated_at,
                          next_attempt_at, last_error_at, dead_lettered_at
                """,
                errorMessage,
                backoffSeconds,
                tenantId.value(),
                documentId,
                requestVersion);
    }

    @Override
    public long countByStatus(IngestionStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ingestions WHERE status = ?",
                Long.class,
                status.name());
        return count != null ? count : 0L;
    }

    private Optional<IngestionJob> queryOne(String sql, Object... args) {
        List<IngestionJob> results = jdbcTemplate.query(sql, JOB_ROW_MAPPER, args);
        return results.stream().findFirst();
    }

    private static final class IngestionJobRowMapper implements RowMapper<IngestionJob> {

        @Override
        public IngestionJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new IngestionJob(
                    TenantId.from(rs.getString("tenant_id")),
                    rs.getString("document_id"),
                    rs.getString("content"),
                    IngestionStatus.valueOf(rs.getString("status")),
                    rs.getLong("request_version"),
                    rs.getInt("chunks_indexed"),
                    rs.getString("error_message"),
                    rs.getInt("retry_count"),
                    rs.getInt("max_attempts"),
                    toInstant(rs, "requested_at"),
                    toInstant(rs, "started_at"),
                    toInstant(rs, "completed_at"),
                    toInstant(rs, "updated_at"),
                    toInstant(rs, "next_attempt_at"),
                    toInstant(rs, "last_error_at"),
                    toInstant(rs, "dead_lettered_at"));
        }

        private Instant toInstant(ResultSet rs, String column) throws SQLException {
            var timestamp = rs.getTimestamp(column);
            return timestamp != null ? timestamp.toInstant() : null;
        }
    }
}
