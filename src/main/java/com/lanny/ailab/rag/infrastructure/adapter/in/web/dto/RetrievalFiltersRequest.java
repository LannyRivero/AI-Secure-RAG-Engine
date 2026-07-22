package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload carrying retrieval metadata filters.
 */
public record RetrievalFiltersRequest(
                String documentType,

                String source,

                List<String> tags,

                String owner,

                String classification,

                LocalDate dateFrom,

                LocalDate dateTo) {
}
