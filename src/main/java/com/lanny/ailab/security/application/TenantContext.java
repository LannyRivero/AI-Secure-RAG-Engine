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

    public String getCurrentTenantId() {
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
                return TenantId.from(tenantRaw).value();
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid tenant format");
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not present in token");
    }
}
