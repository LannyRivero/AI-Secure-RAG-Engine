package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Records unauthenticated access attempts before returning the standard 401 response.
 */
@Component
public class AuditAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String QUERY_ID_HEADER = "X-Query-Id";

    private final SecurityAuditService securityAuditService;
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    public AuditAuthenticationEntryPoint(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * Audits the failed authentication attempt and returns {@code 401 Unauthorized}.
     *
     * @param request the incoming HTTP request
     * @param response the HTTP response being written
     * @param authException the authentication failure details
     * @throws IOException if the response cannot be written
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String queryId = resolveQueryId(request);
        response.setHeader(QUERY_ID_HEADER, queryId);

        securityAuditService.publishSecurityEvent(
                "authentication",
                "unauthorized",
                null,
                null,
                queryId,
                request.getMethod(),
                request.getRequestURI(),
                Map.of("reason", authException.getClass().getSimpleName()));

        delegate.commence(request, response, authException);
    }

    private String resolveQueryId(HttpServletRequest request) {
        String incoming = request.getHeader(QUERY_ID_HEADER);
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming.trim();
    }
}
