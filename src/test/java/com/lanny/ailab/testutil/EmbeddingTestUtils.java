package com.lanny.ailab.testutil;

/**
 * Shared test utilities for embedding-related operations.
 *
 * <p>Eliminates duplication of the same helper methods across integration tests
 * that deal with pgvector data.
 */
public final class EmbeddingTestUtils {

    private EmbeddingTestUtils() {}

    /**
     * Creates a synthetic embedding vector of the given dimension filled with 0.1f.
     * Used to insert test rows into the pgvector table without a real embedding model.
     *
     * @param dimensions the number of dimensions (e.g. 1536 for text-embedding-ada-002)
     * @return a float array of the requested size
     */
    public static float[] syntheticEmbedding(int dimensions) {
        float[] e = new float[dimensions];
        for (int i = 0; i < dimensions; i++) e[i] = 0.1f;
        return e;
    }

    /**
     * Converts a float array to the PostgreSQL vector literal format {@code [x,y,z,...]}.
     * Used when inserting test rows via {@code JdbcTemplate} with the {@code ?::vector} cast.
     *
     * @param embedding the float array to convert
     * @return a PostgreSQL vector string, e.g. {@code "[0.1,0.1,0.1]"}
     */
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
