package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.domain.service.ChunkingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;

/**
 * Application service that implements the document ingestion use case.
 *
 * <p>
 * Execution flow: chunk the raw content → generate all embeddings outside the
 * database transaction → upsert into the vector store (delete existing chunks
 * first, then store new ones). This ensures that re-ingesting a document always
 * replaces the previous version atomically at the document level without holding
 * a database transaction open during slow provider calls.
 *
 * <p>
 * If embedding fails, no write transaction is started and the current indexed
 * document remains untouched. If any database write fails after the delete step,
 * the transaction is rolled back and the previous state is restored.
 * 
 */

@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestDocumentService.class);

    private final ChunkingService chunkingService;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final TransactionOperations transactionOperations;

    public IngestDocumentService(
            ChunkingService chunkingService,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentRepositoryPort documentRepositoryPort,
            TransactionOperations transactionOperations) {

        this.chunkingService = chunkingService;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.documentRepositoryPort = documentRepositoryPort;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Executes the document ingestion pipeline atomically.
     *
     * <p>
     * Steps: chunk content → precompute embeddings → open a short transaction for
     * delete existing chunks + store replacements. This keeps external provider
     * latency outside the transaction boundary while preserving atomic replacement
     * at the document level.
     *
     * @param command the ingestion command containing tenantId, documentId and raw
     *                content
     * @return the result with documentId and number of chunks successfully indexed
     */

    @Override
    public IngestDocumentResult execute(IngestDocumentCommand command) {

        var tenantId = command.tenantId();
        String documentId = command.documentId();

        log.info("INGEST_START tenantId={} documentId={}", tenantId.value(), documentId);

        List<String> chunks = chunkingService.chunk(command.content());

        if (chunks.isEmpty()) {
            log.warn("INGEST_EMPTY_CONTENT tenantId={} documentId={}", tenantId.value(), documentId);
            transactionOperations.executeWithoutResult(status ->
                    documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId));
            return new IngestDocumentResult(documentId, 0);
        }

        List<ChunkEmbedding> preparedChunks = new ArrayList<>(chunks.size());
        for (String chunkContent : chunks) {
            float[] embedding = embeddingPort.embed(chunkContent);
            preparedChunks.add(new ChunkEmbedding(chunkContent, embedding));
        }

        transactionOperations.executeWithoutResult(status -> {
            documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);
            for (ChunkEmbedding preparedChunk : preparedChunks) {
                vectorStorePort.store(
                        tenantId,
                        documentId,
                        preparedChunk.content(),
                        preparedChunk.embedding());
            }
        });

        int indexed = preparedChunks.size();

        log.info("INGEST_COMPLETE tenantId={} documentId={} chunksIndexed={}",
                tenantId.value(), documentId, indexed);

        return new IngestDocumentResult(documentId, indexed);
    }

    private record ChunkEmbedding(String content, float[] embedding) {
    }
}
