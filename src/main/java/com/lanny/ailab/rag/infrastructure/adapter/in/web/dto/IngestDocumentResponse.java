package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import com.lanny.ailab.rag.domain.model.IngestionStatus;

/**
 * HTTP response returned when a document ingestion request is accepted.
 */
public record IngestDocumentResponse(
                String documentId,
                IngestionStatus status) {
}
