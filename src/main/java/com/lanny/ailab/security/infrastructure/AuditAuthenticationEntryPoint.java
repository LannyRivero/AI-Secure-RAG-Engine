package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Records unauthenticated access attempts before returning the standard 401 response.
 */
@Component
public class AuditAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityAuditService securityAuditService;

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

        securityAuditService.publishSecurityEvent(
                "authentication",
                "unauthorized",
                null,
                null,
                Map.of("reason", authException.getClass().getSimpleName()));

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
