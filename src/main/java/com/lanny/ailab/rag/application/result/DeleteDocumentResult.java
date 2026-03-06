package com.lanny.ailab.rag.application.result;

public record DeleteDocumentResult(
        String documentId,
        boolean deleted) {

    public static DeleteDocumentResult success(String documentId) {
        return new DeleteDocumentResult(documentId, true);
    }

    public static DeleteDocumentResult notFound(String documentId) {
        return new DeleteDocumentResult(documentId, false);
    }
}
