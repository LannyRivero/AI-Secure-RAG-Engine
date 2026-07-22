package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Maps durable ingestion job rows into application-layer snapshots.
 */
final class PgIngestionJobRowMapper implements RowMapper<IngestionJob> {

    @Override
    public IngestionJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new IngestionJob(
                TenantId.from(rs.getString("tenant_id")),
                rs.getString("document_id"),
                rs.getString("content"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_uri"),
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
