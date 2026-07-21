package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import com.lanny.ailab.rag.domain.model.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HTTP payload that describes how an ingest request should resolve source text.
 */
public record IngestSourceRequest(

        @NotNull(message = "source.type is required")
        SourceType type,

        @Size(max = 2048, message = "source.uri must be <= 2048 characters")
        String uri,

        @Size(max = 5_000_000, message = "source.base64Content must be <= 5000000 characters")
        String base64Content,

        @Size(max = 4096, message = "source.accessToken must be <= 4096 characters")
        String accessToken,

        @Min(value = 1, message = "source.maxPages must be >= 1")
        @Max(value = 20, message = "source.maxPages must be <= 20")
        Integer maxPages) {
}
