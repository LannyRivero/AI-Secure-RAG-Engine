package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.infrastructure.TenantContext;
import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
@SecurityRequirement(name = "bearerAuth")
public class RagController {

    private final QueryRagUseCase queryRagUseCase;
    private final QueryRagWebMapper mapper;
    private final TenantContext tenantContext;
    private final RateLimiterService rateLimiterService;
    private final SecurityAuditService securityAuditService;

    public RagController(
            QueryRagUseCase queryRagUseCase,
            QueryRagWebMapper mapper,
            TenantContext tenantContext,
            RateLimiterService rateLimiterService,
            SecurityAuditService securityAuditService) {

        this.queryRagUseCase = queryRagUseCase;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.rateLimiterService = rateLimiterService;
        this.securityAuditService = securityAuditService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryRagResponse> query(
            @Valid @RequestBody QueryRagRequest request) {

        var tenantId = tenantContext.getCurrentTenantId();
        var principalId = tenantContext.getCurrentPrincipalId();
        var rateLimitDecision = rateLimiterService.consumeQuery(tenantId);

        if (!rateLimitDecision.allowed()) {
            throw new RateLimitExceededException(
                    tenantId.value(),
                    rateLimitDecision.remainingTokens(),
                    rateLimitDecision.retryAfterSeconds());
        }

        var command = mapper.toCommand(request, tenantId);
        var result = queryRagUseCase.execute(command);

        securityAuditService.publishSensitiveOperation(
                "rag.query",
                result.hasEvidence() ? "success" : "no_evidence",
                tenantId,
                principalId,
                "knowledge_base",
                tenantId.value(),
                java.util.Map.of(
                        "topK", String.valueOf(command.topK() != null ? command.topK() : -1),
                        "hasEvidence", String.valueOf(result.hasEvidence()),
                        "evidenceCount", String.valueOf(result.evidence().size())));

        return ResponseEntity.ok()
                .header("X-Rate-Limit-Remaining", String.valueOf(rateLimitDecision.remainingTokens()))
                .body(mapper.toResponse(result));
    }
}
