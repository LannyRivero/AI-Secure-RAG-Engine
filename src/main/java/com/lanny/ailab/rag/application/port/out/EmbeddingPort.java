package com.lanny.ailab.rag.application.port.out;

public interface EmbeddingPort {

    float[] embed(String text);
}
