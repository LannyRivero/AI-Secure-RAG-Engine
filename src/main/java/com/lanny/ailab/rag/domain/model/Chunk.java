package com.lanny.ailab.rag.domain.model;

import com.lanny.ailab.rag.domain.valueobject.ChunkId;
import com.lanny.ailab.rag.domain.valueobject.DocumentId;
import com.lanny.ailab.rag.domain.valueobject.SimilarityScore;

/**
 * Represents a text fragment derived from a Document.
 *
 * A Chunk is the unit of retrieval: it contains a portion of the
 * original document content and optionally a similarity score
 * when retrieved from the vector store.
 *
 * score is nullable: chunks created during ingestion have no score.
 * Chunks returned from retrieval always have a score.
 */
public class Chunk {

    private final ChunkId id;
    private final DocumentId documentId;
    private final String content;
    private final SimilarityScore score;

    public Chunk(ChunkId id, DocumentId documentId, String content, SimilarityScore score) {
        if (id == null)
            throw new IllegalArgumentException("ChunkId cannot be null");
        if (documentId == null)
            throw new IllegalArgumentException("DocumentId cannot be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.score = score;
    }

    public ChunkId id() {
        return id;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public String content() {
        return content;
    }

    public SimilarityScore score() {
        return score;
    }

    public boolean hasScore() {
        return score != null;
    }
}