package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.*;

public record QueryRagRequest(

                @NotBlank(message = "query is required") @Size(max = 2000, message = "query must be <= 2000 characters") String query,

                @Pattern(regexp = "^[a-fA-F0-9\\-]{36}$", message = "conversationId must be a UUID") String conversationId,

                @Min(value = 1, message = "topK must be >= 1") @Max(value = 20, message = "topK must be <= 20") Integer topK) {
}