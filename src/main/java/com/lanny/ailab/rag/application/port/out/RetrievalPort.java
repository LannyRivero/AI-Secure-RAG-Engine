package com.lanny.ailab.rag.application.port.out;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Outbound port for retrieving semantically relevant document chunks.
 *
 * <p>Implementations may use different retrieval strategies — for example,
 * pure vector search ({@code PgVectorRetriever}) or a hybrid approach combining
 * vector and full-text search ({@code HybridRetriever}).
 */
public interface RetrievalPort {

    /**
     * Retrieves the most relevant chunks for the given query within a specific tenant.
     *
     * @param query    the user's natural language query
     * @param tenantId the owning tenant; restricts results to that tenant's data
     * @param topK     maximum number of chunks to return
     * @return a list of matching {@link DocumentChunk}s ordered by relevance descending,
     *         or an empty list if no results are found
     */
    List<DocumentChunk> retrieve(
            String query,
            TenantId tenantId,
            int topK);
}
