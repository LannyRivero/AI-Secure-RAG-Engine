package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.security.application.AuthenticatedTenantContext;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure component that resolves the authenticated principal and tenant from the current JWT-backed security context.
 *
 * <p>This class lives in infrastructure because it depends directly on Spring Security, HTTP-oriented exceptions, and the concrete JWT claim layout emitted by Keycloak.</p>
 */
@Component
public class TenantContext implements AuthenticatedTenantContext {

    /**
     * Returns the authenticated principal identifier for audit purposes.
     *
     * @return authenticated principal identifier
     * @throws ResponseStatusException with 401 if there is no valid JWT authentication
     */
    @Override
    public String getCurrentPrincipalId() {
        return currentJwtAuthentication().getName();
    }

    /**
     * Extracts and validates the tenant identifier from the current JWT
     * authentication token.
     *
     * <p>
     * Reads the {@code attributes.tenant_id} claim set by Keycloak and returns it
     * as a validated {@link TenantId} value object, guaranteeing that callers
     * always
     * receive a structurally valid tenant identifier.
     *
     * @return the validated {@link TenantId} for the authenticated user
     * @throws ResponseStatusException with 401 if there is no valid authentication
     * @throws ResponseStatusException with 403 if the token lacks a tenant or the
     *                                 tenant format is invalid
     */
    @Override
    public TenantId getCurrentTenantId() {
        JwtAuthenticationToken jwtAuth = currentJwtAuthentication();

        // Keycloak stores user attributes under "attributes" claim as Map<String,
        // List<String>>
        Map<String, Object> attributes = jwtAuth.getToken().getClaim("attributes");

        if (attributes != null && attributes.containsKey("tenant_id")) {
            Object value = attributes.get("tenant_id");
            String tenantRaw = (value instanceof List<?> list && !list.isEmpty())
                    ? list.get(0).toString()
                    : value.toString();

            try {
                MDC.put("tenantId", tenantRaw);
                return TenantId.from(tenantRaw);
            } catch (IllegalArgumentException ex) {
                MDC.remove("tenantId");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid tenant format");
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not present in token");
    }

    private JwtAuthenticationToken currentJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication type");
        }

        return jwtAuth;
    }
}
