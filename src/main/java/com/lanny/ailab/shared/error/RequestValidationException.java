package com.lanny.ailab.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-facing validation error carrying field-level messages.
 */
public final class RequestValidationException extends IllegalArgumentException {

    private final Map<String, String> errors;

    public RequestValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public RequestValidationException(Map<String, String> errors) {
        super("Request validation failed");
        this.errors = Map.copyOf(new LinkedHashMap<>(errors));
    }

    public Map<String, String> errors() {
        return errors;
    }
}
