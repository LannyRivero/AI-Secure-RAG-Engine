package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
                new DocumentMetadata(
                        rs.getString("metadata_document_type"),
                        toLocalDate(rs, "metadata_document_date"),
                        rs.getString("metadata_source"),
                        toStringList(rs, "metadata_tags"),
                        rs.getString("metadata_owner"),
                        rs.getString("metadata_classification")),
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

    private LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        var date = rs.getDate(column);
        return date != null ? date.toLocalDate() : null;
    }

    private List<String> toStringList(ResultSet rs, String column) throws SQLException {
        var array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }
}
