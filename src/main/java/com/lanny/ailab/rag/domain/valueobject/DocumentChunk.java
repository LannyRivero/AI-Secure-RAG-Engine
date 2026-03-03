package com.lanny.ailab.rag.domain.valueobject;

public record DocumentChunk(
                String documentId,
                String tenantId,
                String content,
                double score) {
}