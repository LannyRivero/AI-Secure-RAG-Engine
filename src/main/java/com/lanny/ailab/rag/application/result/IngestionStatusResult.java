package com.lanny.ailab.rag.application.result;

import com.lanny.ailab.rag.domain.model.IngestionStatus;

import java.time.Instant;

/**
 * Read model returned when clients query the state of a document ingestion.
 *
 * @param documentId     document identifier
 * @param status         current lifecycle status
 * @param chunksIndexed  final indexed chunk count when completed, otherwise
 *                       zero
 * @param errorMessage   failure detail when status is not successful, otherwise
 *                       {@code null}
 * @param retryCount     number of failed attempts already consumed
 * @param maxAttempts    maximum configured attempts for this job
 * @param requestedAt    latest request enqueue instant
 * @param startedAt      active attempt start instant, or {@code null}
 * @param completedAt    completion instant, or {@code null}
 * @param updatedAt      last status mutation instant
 * @param nextAttemptAt  next eligible processing instant
 * @param lastErrorAt    last failure instant, or {@code null}
 * @param deadLetteredAt dead-letter instant, or {@code null}
 */
public record IngestionStatusResult(
                String documentId,
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
