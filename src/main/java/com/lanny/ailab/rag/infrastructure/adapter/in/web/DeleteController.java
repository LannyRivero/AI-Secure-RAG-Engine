package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for document deletion operations.
 *
 * <p>
 * Exposes a single DELETE endpoint scoped to the authenticated tenant.
 * The {@code documentId} path variable is validated against the same pattern
 * used during ingestion to ensure consistent input contracts across all entry
 * points.
 *
 * <p>
 * {@code @Validated} at class level is required to activate Bean Validation
 * on method parameters (path variables and request params). Without it,
 * {@code @Pattern} on the path variable is silently ignored by Spring MVC.
 */
@RestController
@RequestMapping("/rag")
@SecurityRequirement(name = "bearerAuth")
@Validated
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

    /**
     * Deletes all indexed chunks for the given document within the authenticated
     * tenant's scope.
     *
     * <p>
     * The operation is idempotent: if the document does not exist, 404 is returned
     * without throwing an exception. The tenant is always extracted from the JWT —
     * callers cannot delete documents from other tenants.
     *
     * @param documentId the identifier of the document to delete;
     *                   must match {@code ^[a-zA-Z0-9_-]{1,100}$}
     * @return 204 No Content if deleted, 404 Not Found if the document did not
     *         exist
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "documentId must contain only alphanumeric characters, hyphens or underscores (max 100 chars)") String documentId) {

        var tenantId = tenantContext.getCurrentTenantId();

        if (!rateLimiterService.tryConsumeIngest(tenantId)) {
            throw new RateLimitExceededException(tenantId.value());
        }

        var command = new DeleteDocumentCommand(documentId, tenantId);
        var result = deleteDocumentUseCase.execute(command);

        return result.deleted()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}