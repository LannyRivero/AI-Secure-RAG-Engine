package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.domain.service.ChunkingService;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service that implements the document ingestion use case.
 *
 * <p>
 * Execution flow: chunk the raw content → embed each chunk → upsert into the
 * vector store
 * (delete existing chunks first, then store new ones). This ensures that
 * re-ingesting a document
 * always replaces the previous version atomically at the document level.
 *
 * <p>
 * The entire operation runs in a single database transaction. If embedding or
 * storage fails
 * for any chunk, the transaction is rolled back and the document retains its
 * previous state.
 * This prevents partial ingestion where some chunks are stored and others are
 * not.
 *
 * <p>
 * Trade-off: the database connection remains open while calling the OpenAI
 * embedding API
 * (up to 10s per chunk). Acceptable for MVP with a small connection pool. At
 * higher scale,
 * pre-fetch all embeddings before opening the transaction.
 * 
 */

@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestDocumentService.class);

    private final ChunkingService chunkingService;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentRepositoryPort documentRepositoryPort;

    public IngestDocumentService(
            ChunkingService chunkingService,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentRepositoryPort documentRepositoryPort) {

        this.chunkingService = chunkingService;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.documentRepositoryPort = documentRepositoryPort;
    }

    /**
     * Executes the document ingestion pipeline atomically.
     *
     * <p>
     * Steps: delete existing chunks → chunk content → embed each chunk → store each
     * chunk.
     * The full pipeline runs inside a single database transaction so that failure
     * at any step
     * leaves the document in its previous consistent state rather than partially
     * updated.
     *
     * @param command the ingestion command containing tenantId, documentId and raw
     *                content
     * @return the result with documentId and number of chunks successfully indexed
     */

    @Override
    @Transactional
    public IngestDocumentResult execute(IngestDocumentCommand command) {

        var tenantId = command.tenantId();
        String documentId = command.documentId();

        log.info("INGEST_START tenantId={} documentId={}", tenantId.value(), documentId);

        // Upsert: delete existing chunks for this document before re-indexing
        // Runs inside the same transaction — if a subsequent store fails,
        // this delete is also rolled back.
        documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);

        List<String> chunks = chunkingService.chunk(command.content());

        if (chunks.isEmpty()) {
            log.warn("INGEST_EMPTY_CONTENT tenantId={} documentId={}", tenantId.value(), documentId);
            return new IngestDocumentResult(documentId, 0);
        }

        int indexed = 0;
        for (String chunkContent : chunks) {
            float[] embedding = embeddingPort.embed(chunkContent);
            vectorStorePort.store(tenantId, documentId, chunkContent, embedding);
            indexed++;
        }

        log.info("INGEST_COMPLETE tenantId={} documentId={} chunksIndexed={}",
                tenantId.value(), documentId, indexed);

        return new IngestDocumentResult(documentId, indexed);
    }
}
