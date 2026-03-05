package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

public record IngestDocumentResponse(
        String documentId,
        int chunksIndexed) {
}