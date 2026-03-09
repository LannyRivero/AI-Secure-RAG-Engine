package com.lanny.ailab.rag.domain.valueobject;

public record DocumentChunk(
                String documentId,
                TenantId tenantId,
                String content,
                double score) {
}