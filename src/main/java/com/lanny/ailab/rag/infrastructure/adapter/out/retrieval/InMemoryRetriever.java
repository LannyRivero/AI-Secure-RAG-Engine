package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

import java.util.List;

public class InMemoryRetriever implements RetrievalPort {

    @Override
    public List<DocumentChunk> retrieve(String query, String tenantId, int topK) {

        return List.of(
                new DocumentChunk(
                        "doc-1",
                        tenantId,
                        "La energía solar convierte la luz del sol en electricidad."
                )
        );
    }
}
