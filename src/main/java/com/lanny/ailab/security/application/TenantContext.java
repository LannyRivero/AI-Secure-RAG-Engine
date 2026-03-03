package com.lanny.ailab.security.application;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TenantContext {

    public String getCurrentTenantId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("No JWT authentication found in context");
        }

        Jwt jwt = jwtAuth.getToken();

        Map<String, Object> attributes = jwt.getClaim("attributes");
        if (attributes != null && attributes.containsKey("tenant_id")) {
            Object value = attributes.get("tenant_id");
            if (value instanceof List<?> list && !list.isEmpty()) {
                return list.get(0).toString();
            }
            return value.toString();
        }

        throw new IllegalStateException("tenant_id claim not found in JWT");
    }
}
