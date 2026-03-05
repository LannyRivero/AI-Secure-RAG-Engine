package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class DeleteController {

    private final DeleteDocumentUseCase deleteDocumentUseCase;
    private final TenantContext tenantContext;
    private final RateLimiterService rateLimiterService;

    public DeleteController(
            DeleteDocumentUseCase deleteDocumentUseCase,
            TenantContext tenantContext,
            RateLimiterService rateLimiterService) {

        this.deleteDocumentUseCase = deleteDocumentUseCase;
        this.tenantContext = tenantContext;
        this.rateLimiterService = rateLimiterService;
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        String tenantId = tenantContext.getCurrentTenantId();

        if (!rateLimiterService.tryConsumeIngest(tenantId)) {
            throw new RateLimitExceededException(tenantId);
        }

        var command = new DeleteDocumentCommand(documentId, TenantId.from(tenantId));
        var result = deleteDocumentUseCase.execute(command);

        return result.deleted()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}