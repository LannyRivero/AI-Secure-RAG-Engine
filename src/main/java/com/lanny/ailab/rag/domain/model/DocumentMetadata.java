package com.lanny.ailab.rag.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Structured business metadata attached to an ingested document.
 *
 * <p>
 * This metadata is duplicated into indexed chunks so retrieval can filter by
 * product-level facets without leaving tenant isolation behind.
 *
 * @param documentType   product-defined document category
 * @param documentDate   business date associated with the document
 * @param source         human-facing source label distinct from connector
 *                       metadata
 * @param tags           normalized tag set used for faceted retrieval
 * @param owner          document owner or maintainer
 * @param classification document sensitivity or classification level
 */
public record DocumentMetadata(
                String documentType,
                LocalDate documentDate,
                String source,
                List<String> tags,
                String owner,
                String classification) {

        public DocumentMetadata {
                documentType = MetadataFieldRules.normalizeOptionalText(documentType, "documentType",
                                MetadataFieldRules.MAX_DOCUMENT_TYPE_LENGTH);
                source = MetadataFieldRules.normalizeOptionalText(source, "source",
                                MetadataFieldRules.MAX_SOURCE_LENGTH);
                owner = MetadataFieldRules.normalizeOptionalText(owner, "owner",
                                MetadataFieldRules.MAX_OWNER_LENGTH);
                classification = MetadataFieldRules.normalizeOptionalText(classification, "classification",
                                MetadataFieldRules.MAX_CLASSIFICATION_LENGTH);
                tags = MetadataFieldRules.normalizeOptionalTags(tags, "tags");
        }

        /**
         * Returns an empty metadata instance.
         *
         * @return metadata with every optional facet unset
         */
        public static DocumentMetadata empty() {
                return new DocumentMetadata(null, null, null, List.of(), null, null);
        }

        /**
         * Indicates whether at least one metadata facet is present.
         *
         * @return {@code true} when the metadata carries any business facet
         */
        public boolean hasValues() {
                return documentType != null
                                || documentDate != null
                                || source != null
                                || !tags.isEmpty()
                                || owner != null
                                || classification != null;
        }
}
