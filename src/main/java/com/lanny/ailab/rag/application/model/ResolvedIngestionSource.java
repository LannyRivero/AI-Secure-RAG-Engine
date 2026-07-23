package com.lanny.ailab.rag.application.model;

import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.model.SourceType;

/**
 * Normalized source content ready to enter the existing durable ingestion
 * queue.
 *
 * @param content    extracted text to index
 * @param sourceType connector kind used to resolve the text
 * @param sourceUri  original source locator when available
 * @param metadata   normalized business metadata to persist with the document
 */
public record ResolvedIngestionSource(
        String content,
        SourceType sourceType,
        String sourceUri,
        DocumentMetadata metadata) {

    public ResolvedIngestionSource {
        metadata = metadata == null ? DocumentMetadata.empty() : metadata;
    }
}
