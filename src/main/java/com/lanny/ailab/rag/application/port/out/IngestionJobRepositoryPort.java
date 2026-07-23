package com.lanny.ailab.rag.application.port.out;

import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.application.command.EnqueueIngestionJobCommand;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import java.util.Optional;

/**
 * Outbound port for durable ingestion job orchestration.
 */
public interface IngestionJobRepositoryPort {

        /**
         * Creates or replaces the latest requested ingestion payload for a document.
         *
         * @param command requested document ingestion payload
         * @return the persisted job snapshot after enqueueing
         */
        IngestionJob enqueue(EnqueueIngestionJobCommand command);

        /**
         * Loads the current ingestion state for the given tenant and document.
         *
         * @param tenantId   owning tenant
         * @param documentId document identifier
         * @return current job snapshot when one exists
         */
        Optional<IngestionJob> findByTenantAndDocument(TenantId tenantId, String documentId);

        /**
         * Atomically claims the next pending job for background processing.
         *
         * @return claimed job if one was available
         */
        Optional<IngestionJob> claimNextPending();

        /**
         * Loads and row-locks the current job inside a transaction.
         *
         * @param tenantId   owning tenant
         * @param documentId document identifier
         * @return locked job snapshot when it exists
         */
        Optional<IngestionJob> findByTenantAndDocumentForUpdate(TenantId tenantId, String documentId);

        /**
         * Marks a claimed version as completed.
         *
         * @param tenantId               owning tenant
         * @param documentId             document identifier
         * @param requestVersion         claimed version to finalize
         * @param processingLeaseVersion claim lease version for the active attempt
         * @param chunksIndexed          indexed chunk count written by the successful
         *                               run
         * @return {@code true} when the row was updated, {@code false} when a newer
         *         version already replaced it
         */
        boolean markCompleted(TenantId tenantId, String documentId, long requestVersion, long processingLeaseVersion,
                        int chunksIndexed);

        /**
         * Records a failed attempt and reschedules or dead-letters the claimed version.
         *
         * @param tenantId               owning tenant
         * @param documentId             document identifier
         * @param requestVersion         claimed version to finalize
         * @param processingLeaseVersion claim lease version for the active attempt
         * @param errorMessage           failure detail for diagnostics
         * @param backoffSeconds         delay before the next retry; ignored when the
         *                               job is
         *                               dead-lettered
         * @return updated job snapshot when the row was updated, or empty when a newer
         *         version already replaced it
         */
        Optional<IngestionJob> markFailed(TenantId tenantId, String documentId, long requestVersion,
                        long processingLeaseVersion, String errorMessage, long backoffSeconds);

        /**
         * Releases a claimed processing row back to pending without recording a
         * failure.
         *
         * @param tenantId               owning tenant
         * @param documentId             document identifier
         * @param requestVersion         claimed version being released
         * @param processingLeaseVersion claim lease version for the active attempt
         * @return {@code true} when the row was released, {@code false} when it was
         *         already superseded
         */
    boolean releaseClaim(TenantId tenantId, String documentId, long requestVersion, long processingLeaseVersion);

    /**
     * Renews the lease of a currently processing job attempt.
     *
     * @param tenantId owning tenant
     * @param documentId document identifier
     * @param requestVersion claimed version being processed
     * @param processingLeaseVersion active lease version for the attempt
     * @return {@code true} when the lease was renewed, {@code false} when the attempt no longer owns the row
     */
    boolean renewClaimLease(TenantId tenantId, String documentId, long requestVersion, long processingLeaseVersion);

        /**
         * Counts jobs currently in the requested state.
         *
         * @param status lifecycle state to count
         * @return number of rows currently in that state
         */
        long countByStatus(com.lanny.ailab.rag.domain.model.IngestionStatus status);
}
