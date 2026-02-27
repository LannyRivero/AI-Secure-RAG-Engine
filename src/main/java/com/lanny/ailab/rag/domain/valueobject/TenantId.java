package com.lanny.ailab.rag.domain.valueobject;

import java.util.Objects;

public final class TenantId {

    private final String value;

    public TenantId(String value) {
        Objects.requireNonNull(value, "TenantId cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be blank");
        }

        if (!value.matches("^[a-zA-Z0-9\\-]{3,50}$")) {
            throw new IllegalArgumentException("Invalid TenantId format");
        }

        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TenantId that))
            return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
