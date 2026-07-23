package com.lanny.ailab.rag.domain.model;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Shared normalization and validation rules for document metadata facets.
 */
public final class MetadataFieldRules {

    public static final int MAX_DOCUMENT_TYPE_LENGTH = 100;
    public static final int MAX_SOURCE_LENGTH = 255;
    public static final int MAX_OWNER_LENGTH = 100;
    public static final int MAX_CLASSIFICATION_LENGTH = 100;
    public static final int MAX_TAGS = 20;
    public static final int MAX_TAG_LENGTH = 50;

    private MetadataFieldRules() {
    }

    /**
     * Normalizes and validates an optional facet string.
     *
     * @param value    raw input value
     * @param field    transport-neutral field name used in validation messages
     * @param maxChars maximum allowed length
     * @return normalized value or {@code null} when absent
     */
    public static String normalizeOptionalText(String value, String field, int maxChars) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new FacetValidationException(field, field + " cannot be blank when provided");
        }
        if (trimmed.length() > maxChars) {
            throw new FacetValidationException(field, field + " must be <= " + maxChars + " characters");
        }
        return trimmed;
    }

    /**
     * Normalizes and validates an optional tag list.
     *
     * @param rawTags raw tags supplied by callers
     * @param field   transport-neutral field name used in validation messages
     * @return normalized distinct tags preserving input order
     */
    public static List<String> normalizeOptionalTags(List<String> rawTags, String field) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        if (rawTags.size() > MAX_TAGS) {
            throw new FacetValidationException(field, field + " must contain at most " + MAX_TAGS + " entries");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag == null) {
                throw new FacetValidationException(field, field + " entries cannot be null");
            }
            normalized.add(normalizeOptionalText(rawTag, field + "[]", MAX_TAG_LENGTH));
        }
        return List.copyOf(normalized);
    }
}
