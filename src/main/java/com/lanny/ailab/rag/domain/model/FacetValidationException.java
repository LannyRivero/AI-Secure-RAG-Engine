package com.lanny.ailab.rag.domain.model;

/**
 * Signals an invalid metadata or retrieval facet using transport-neutral field names.
 */
public final class FacetValidationException extends IllegalArgumentException {

    private final String field;

    public FacetValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
