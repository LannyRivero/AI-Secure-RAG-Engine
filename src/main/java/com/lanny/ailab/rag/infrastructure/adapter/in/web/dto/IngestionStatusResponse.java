package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;

import java.time.Instant;

/**
 * HTTP response exposing the durable state of a document ingestion.
 */
public record IngestionStatusResponse(
                String documentId,
                SourceType sourceType,
                String sourceUri,
                IngestionStatus status,
                int chunksIndexed,
                String errorMessage,
                int retryCount,
                int maxAttempts,
                Instant requestedAt,
                Instant startedAt,
                Instant completedAt,
                Instant updatedAt,
                Instant nextAttemptAt,
                Instant lastErrorAt,
                Instant deadLetteredAt) {
}
