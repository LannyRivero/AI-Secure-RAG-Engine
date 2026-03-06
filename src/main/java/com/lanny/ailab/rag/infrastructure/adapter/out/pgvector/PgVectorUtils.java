package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

public final class PgVectorUtils {

    private PgVectorUtils() {}

    public static String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}