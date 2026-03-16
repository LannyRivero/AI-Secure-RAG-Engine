package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final QueryRagUseCase queryRagUseCase;
    private final QueryRagWebMapper mapper;
    private final TenantContext tenantContext;
    private final RateLimiterService rateLimiterService;

    public RagController(
            QueryRagUseCase queryRagUseCase,
            QueryRagWebMapper mapper,
            TenantContext tenantContext,
            RateLimiterService rateLimiterService) {

        this.queryRagUseCase = queryRagUseCase;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryRagResponse> query(
            @Valid @RequestBody QueryRagRequest request) {

        var tenantId = tenantContext.getCurrentTenantId();

        if (!rateLimiterService.tryConsumeQuery(tenantId.value())) {
            throw new RateLimitExceededException(tenantId.value());
        }

        var command = mapper.toCommand(request, tenantId);
        var result = queryRagUseCase.execute(command);

        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
