package com.lanny.ailab.security.application;

import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public class TenantContext {

    private static final String TENANT_CLAIM = "tenant_id";

    public String getCurrentTenantId() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication type");
        }

        String tenantRaw = jwtAuth.getToken().getClaimAsString(TENANT_CLAIM);

        if (tenantRaw == null || tenantRaw.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tenant not present in token");
        }

        try {
            return TenantId.from(tenantRaw).value();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid tenant format");
        }
    }
}
