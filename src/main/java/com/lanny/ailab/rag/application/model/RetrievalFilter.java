package com.lanny.ailab.rag.application.model;

import com.lanny.ailab.rag.domain.model.MetadataFieldRules;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured retrieval filters applied in addition to tenant isolation.
 *
 * @param documentType   required document type when set
 * @param source         required business source when set
 * @param tags           required tags; all provided tags must be present
 * @param owner          required owner when set
 * @param classification required classification when set
 * @param dateFrom       lower bound for documentDate when set
 * @param dateTo         upper bound for documentDate when set
 */
public record RetrievalFilter(
                String documentType,
                String source,
                List<String> tags,
                String owner,
                String classification,
                LocalDate dateFrom,
                LocalDate dateTo) {

        public RetrievalFilter {
                documentType = MetadataFieldRules.normalizeOptionalText(documentType, "filters.documentType",
                                MetadataFieldRules.MAX_DOCUMENT_TYPE_LENGTH);
                source = MetadataFieldRules.normalizeOptionalText(source, "filters.source",
                                MetadataFieldRules.MAX_SOURCE_LENGTH);
                owner = MetadataFieldRules.normalizeOptionalText(owner, "filters.owner",
                                MetadataFieldRules.MAX_OWNER_LENGTH);
                classification = MetadataFieldRules.normalizeOptionalText(classification, "filters.classification",
                                MetadataFieldRules.MAX_CLASSIFICATION_LENGTH);
                tags = MetadataFieldRules.normalizeOptionalTags(tags, "filters.tags");
                if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
                        throw new IllegalArgumentException(
                                        "filters.dateFrom must be before or equal to filters.dateTo");
                }
        }

        /**
         * Returns an empty filter set.
         *
         * @return filters with every optional constraint unset
         */
        public static RetrievalFilter empty() {
                return new RetrievalFilter(null, null, List.of(), null, null, null, null);
        }

        /**
         * Indicates whether any retrieval facet was requested.
         *
         * @return {@code true} when at least one filter is active
         */
        public boolean hasFilters() {
                return documentType != null
                                || source != null
                                || !tags.isEmpty()
                                || owner != null
                                || classification != null
                                || dateFrom != null
                                || dateTo != null;
        }
}
