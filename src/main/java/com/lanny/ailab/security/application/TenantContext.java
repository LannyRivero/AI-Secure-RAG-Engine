package com.lanny.ailab.security.application;

import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class TenantContext {

    /**
     * Extracts and validates the tenant identifier from the current JWT authentication token.
     *
     * <p>Reads the {@code attributes.tenant_id} claim set by Keycloak and returns it
     * as a validated {@link TenantId} value object, guaranteeing that callers always
     * receive a structurally valid tenant identifier.
     *
     * @return the validated {@link TenantId} for the authenticated user
     * @throws ResponseStatusException with 401 if there is no valid authentication
     * @throws ResponseStatusException with 403 if the token lacks a tenant or the tenant format is invalid
     */
    public TenantId getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication type");
        }

        // Keycloak stores user attributes under "attributes" claim as Map<String, List<String>>
        Map<String, Object> attributes = jwtAuth.getToken().getClaim("attributes");

        if (attributes != null && attributes.containsKey("tenant_id")) {
            Object value = attributes.get("tenant_id");
            String tenantRaw = (value instanceof List<?> list && !list.isEmpty())
                    ? list.get(0).toString()
                    : value.toString();

            try {
                return TenantId.from(tenantRaw);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid tenant format");
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not present in token");
    }
}
