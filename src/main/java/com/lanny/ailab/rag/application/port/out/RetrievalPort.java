package com.lanny.ailab.rag.application.port.out;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

public interface RetrievalPort {

    List<DocumentChunk> retrieve(
            String query,
            TenantId tenantId,
            int topK);
}
