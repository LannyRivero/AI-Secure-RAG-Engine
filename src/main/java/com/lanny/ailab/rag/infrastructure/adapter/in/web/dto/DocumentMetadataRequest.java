package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload carrying structured business metadata for ingestion.
 */
public record DocumentMetadataRequest(
                String documentType,

                LocalDate documentDate,

                String source,

                List<String> tags,

                String owner,

                String classification) {
}
