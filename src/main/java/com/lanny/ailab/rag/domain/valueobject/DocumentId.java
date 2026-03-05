package com.lanny.ailab.rag.domain.valueobject;

import java.util.Objects;

public final class DocumentId {

    private final String value;

    private DocumentId(String value) {
        this.value = value;
    }

    public static DocumentId from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DocumentId cannot be null or blank");
        }
        return new DocumentId(raw);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentId)) return false;
        return value.equals(((DocumentId) o).value);
    }

    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value; }
}