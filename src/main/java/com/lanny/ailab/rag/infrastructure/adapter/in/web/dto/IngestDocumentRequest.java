package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IngestDocumentRequest(

        @NotBlank(message = "documentId is required")
        @Pattern(
            regexp = "^[a-zA-Z0-9_-]{1,100}$",
            message = "documentId must contain only alphanumeric characters, hyphens or underscores (max 100 chars)"
        )
        String documentId,

        @NotBlank(message = "content is required")
        @Size(max = 100_000, message = "content must be <= 100000 characters")
        String content) {
}