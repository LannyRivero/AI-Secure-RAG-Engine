package com.lanny.ailab.rag.application.port.out;

public interface VectorStorePort {

    void store(String tenantId, String documentId, String content, float[] embedding);

}
