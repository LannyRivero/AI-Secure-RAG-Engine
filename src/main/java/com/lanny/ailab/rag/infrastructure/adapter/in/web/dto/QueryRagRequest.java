package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QueryRagRequest(

                @NotBlank(message = "Query cannot be blank") @Size(max = 2000, message = "Query must not exceed 2000 characters") String query,

                @NotBlank(message = "TenantId cannot be blank") @Pattern(regexp = "^[a-zA-Z0-9\\-]{3,50}$", message = "TenantId must be alphanumeric and between 3 and 50 characters") String tenantId

) {
}