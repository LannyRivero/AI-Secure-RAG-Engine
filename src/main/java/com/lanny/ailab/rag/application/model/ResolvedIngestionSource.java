package com.lanny.ailab.rag.application.model;

import com.lanny.ailab.rag.domain.model.SourceType;

/**
 * Normalized source content ready to enter the existing durable ingestion queue.
 *
 * @param content    extracted text to index
 * @param sourceType connector kind used to resolve the text
 * @param sourceUri  original source locator when available
 */
public record ResolvedIngestionSource(
        String content,
        SourceType sourceType,
        String sourceUri) {
}
