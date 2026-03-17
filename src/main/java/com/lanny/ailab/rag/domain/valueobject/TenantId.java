package com.lanny.ailab.rag.domain.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a validated tenant identifier.
 *
 * <p>A tenant ID must match the pattern {@code ^[a-zA-Z0-9_-]{3,50}$}.
 * This constraint prevents SQL injection through tenant values and ensures
 * safe usage as a URL path segment or database column value.
 *
 * <p>All output ports and application services accept {@code TenantId} rather than
 * raw {@code String} to guarantee that the value has been validated before use.
 */
public final class TenantId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    private final String value;

    private TenantId(String value) {
        this.value = value;
    }

    /**
     * Factory method that validates and constructs a {@code TenantId}.
     *
     * @param raw the raw tenant string to validate
     * @return a validated {@code TenantId} instance
     * @throws IllegalArgumentException if {@code raw} is null, blank, or does not match the allowed pattern
     */
    public static TenantId from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be null or blank");
        }

        if (!VALID_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException("Invalid TenantId format");
        }

        return new TenantId(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TenantId))
            return false;
        TenantId tenantId = (TenantId) o;
        return value.equals(tenantId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}