package com.lanny.ailab.rag.domain.model;

/**
 * Lifecycle states for an asynchronous document ingestion job.
 */
public enum IngestionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
