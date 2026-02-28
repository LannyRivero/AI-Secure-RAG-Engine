package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class InMemoryRetriever implements RetrievalPort {

    @Override
    public List<DocumentChunk> retrieve(String query, String tenantId, int topK) {

        String content = "La energía solar convierte la luz del sol en electricidad.";
        double score = query.toLowerCase().contains("solar") ? 0.9 : 0.2;


        return List.of(
                new DocumentChunk(
                        "doc-1",
                        tenantId,
                        content,
                        score
                )
        );
    }
}
