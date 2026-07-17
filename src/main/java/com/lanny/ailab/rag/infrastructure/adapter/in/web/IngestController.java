package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestionStatusResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.IngestDocumentWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/rag")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class IngestController {

    private final IngestDocumentUseCase ingestDocumentUseCase;
    private final GetIngestionStatusUseCase getIngestionStatusUseCase;
    private final IngestDocumentWebMapper mapper;
    private final TenantContext tenantContext;
    private final RateLimiterService rateLimiterService;

    public IngestController(
            IngestDocumentUseCase ingestDocumentUseCase,
            GetIngestionStatusUseCase getIngestionStatusUseCase,
            IngestDocumentWebMapper mapper,
            TenantContext tenantContext,
            RateLimiterService rateLimiterService) {

        this.ingestDocumentUseCase = ingestDocumentUseCase;
        this.getIngestionStatusUseCase = getIngestionStatusUseCase;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestDocumentResponse> ingest(@Valid @RequestBody IngestDocumentRequest request) {
        var tenantId = tenantContext.getCurrentTenantId();
        var rateLimitDecision = rateLimiterService.consumeIngest(tenantId);

        if (!rateLimitDecision.allowed()) {
            throw new RateLimitExceededException(
                    tenantId.value(),
                    rateLimitDecision.remainingTokens(),
                    rateLimitDecision.retryAfterSeconds());
        }

        var command = mapper.toCommand(request, tenantId);
        var result = ingestDocumentUseCase.execute(command);
        return ResponseEntity.accepted()
                .header("Location", "/rag/ingest/" + result.documentId())
                .header("X-Rate-Limit-Remaining", String.valueOf(rateLimitDecision.remainingTokens()))
                .body(mapper.toResponse(result));
    }

    @GetMapping("/ingest/{documentId}")
    public ResponseEntity<IngestionStatusResponse> getStatus(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "documentId must contain only alphanumeric characters, hyphens or underscores (max 100 chars)") String documentId) {

        var tenantId = tenantContext.getCurrentTenantId();
        return getIngestionStatusUseCase.findStatus(tenantId, documentId)
                .map(mapper::toStatusResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
