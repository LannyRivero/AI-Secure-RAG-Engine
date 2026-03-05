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

import java.util.List;

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

        this.chunkingService        = chunkingService;
        this.embeddingPort          = embeddingPort;
        this.vectorStorePort        = vectorStorePort;
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    public IngestDocumentResult execute(IngestDocumentCommand command) {

        String tenantId   = command.tenantId().value();
        String documentId = command.documentId();

        log.info("INGEST_START tenantId={} documentId={}", tenantId, documentId);

        // Upsert: delete existing chunks for this document before re-indexing
        documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);

        List<String> chunks = chunkingService.chunk(command.content());

        if (chunks.isEmpty()) {
            log.warn("INGEST_EMPTY_CONTENT tenantId={} documentId={}", tenantId, documentId);
            return new IngestDocumentResult(documentId, 0);
        }

        int indexed = 0;
        for (String chunkContent : chunks) {
            float[] embedding = embeddingPort.embed(chunkContent);
            vectorStorePort.store(tenantId, documentId, chunkContent, embedding);
            indexed++;
        }

        log.info("INGEST_COMPLETE tenantId={} documentId={} chunksIndexed={}", 
                tenantId, documentId, indexed);

        return new IngestDocumentResult(documentId, indexed);
    }
}
