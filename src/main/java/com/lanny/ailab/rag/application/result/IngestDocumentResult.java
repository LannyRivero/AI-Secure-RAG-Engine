package com.lanny.ailab.rag.application.result;

public record IngestDocumentResult(
        String documentId,
        int chunksIndexed) {
}
