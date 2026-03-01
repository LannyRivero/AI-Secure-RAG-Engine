package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;

import org.springframework.stereotype.Service;

@Service
public class DocumentIndexService {

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;

    public DocumentIndexService(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
    }

    public void index(String tenantId, String documentId, String content) {

        float[] embedding = embeddingPort.embed(content);
        vectorStorePort.store(tenantId, documentId, content, embedding);

    }
}