package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Records authorization failures before returning the standard 403 response.
 */
@Component
public class AuditAccessDeniedHandler implements AccessDeniedHandler {

    private static final String QUERY_ID_HEADER = "X-Query-Id";

    private final SecurityAuditService securityAuditService;

    public AuditAccessDeniedHandler(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * Audits the denied access and returns {@code 403 Forbidden}.
     *
     * @param request the incoming HTTP request
     * @param response the HTTP response being written
     * @param accessDeniedException the authorization failure details
     * @throws IOException if the response cannot be written
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalId = authentication != null ? authentication.getName() : null;
        String queryId = resolveQueryId(request);

        response.setHeader(QUERY_ID_HEADER, queryId);

        securityAuditService.publishSecurityEvent(
                "authorization",
                "forbidden",
                principalId,
                null,
                queryId,
                request.getMethod(),
                request.getRequestURI(),
                Map.of("reason", accessDeniedException.getClass().getSimpleName()));

        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private String resolveQueryId(HttpServletRequest request) {
        String incoming = request.getHeader(QUERY_ID_HEADER);
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming.trim();
    }
}
