package com.lanny.ailab.rag.application.port.out;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

public interface RetrievalPort {

    List<DocumentChunk> retrieve(
            String query,
            String tenantId,
            int topK);
}
