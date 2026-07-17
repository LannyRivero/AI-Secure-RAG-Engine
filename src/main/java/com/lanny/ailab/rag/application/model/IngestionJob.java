package com.lanny.ailab.rag.application.model;

import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import java.time.Instant;

/**
 * Snapshot of a persisted ingestion job used by the application layer.
 *
 * @param tenantId       owning tenant
 * @param documentId     document identifier within the tenant scope
 * @param content        latest content requested for ingestion
 * @param status         persisted lifecycle status
 * @param requestVersion monotonically increasing version for stale-work
 *                       detection
 * @param chunksIndexed  final indexed chunk count when completed, otherwise
 *                       zero
 * @param errorMessage   failure detail for diagnostics, or {@code null}
 * @param retryCount     number of failed attempts already recorded
 * @param maxAttempts    maximum attempts allowed before dead-lettering
 * @param requestedAt    first or latest enqueue instant
 * @param startedAt      processing start instant for the active attempt, or
 *                       {@code null}
 * @param completedAt    completion instant, or {@code null}
 * @param updatedAt      last state mutation instant
 * @param nextAttemptAt  next eligible execution instant
 * @param lastErrorAt    instant of last failure, or {@code null}
 * @param deadLetteredAt instant the job was moved to dead letter, or
 *                       {@code null}
 */
public record IngestionJob(
                TenantId tenantId,
                String documentId,
                String content,
                IngestionStatus status,
                long requestVersion,
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
