package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.IngestDocumentWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class IngestController {

    private final IngestDocumentUseCase ingestDocumentUseCase;
    private final IngestDocumentWebMapper mapper;
    private final TenantContext tenantContext;
    private final RateLimiterService rateLimiterService;

    public IngestController(
            IngestDocumentUseCase ingestDocumentUseCase,
            IngestDocumentWebMapper mapper,
            TenantContext tenantContext,
            RateLimiterService rateLimiterService) {

        this.ingestDocumentUseCase = ingestDocumentUseCase;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestDocumentResponse ingest(@Valid @RequestBody IngestDocumentRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();

        if (!rateLimiterService.tryConsumeIngest(tenantId)) {
            throw new RateLimitExceededException(tenantId);
        }

        var command = mapper.toCommand(request, tenantId);
        var result = ingestDocumentUseCase.execute(command);
        return mapper.toResponse(result);
    }
}