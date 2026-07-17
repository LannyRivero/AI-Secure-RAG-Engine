package com.lanny.ailab.rag.application.result;

import com.lanny.ailab.rag.domain.model.IngestionStatus;

/**
 * Result returned immediately after a document ingestion request is accepted.
 *
 * @param documentId document identifier
 * @param status     accepted lifecycle status, typically {@code PENDING}
 */
public record IngestDocumentResult(
                String documentId,
                IngestionStatus status) {
}
