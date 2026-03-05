package com.lanny.ailab.rag.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public final class ChunkId {

    private final String value;

    private ChunkId(String value) {
        this.value = value;
    }

    public static ChunkId generate() {
        return new ChunkId(UUID.randomUUID().toString());
    }

    public static ChunkId from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ChunkId cannot be null or blank");
        }
        return new ChunkId(raw);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkId)) return false;
        return value.equals(((ChunkId) o).value);
    }

    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value; }
}
