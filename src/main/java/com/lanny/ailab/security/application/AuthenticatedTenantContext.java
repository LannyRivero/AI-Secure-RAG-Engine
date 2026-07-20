package com.lanny.ailab.security.application;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Abstraction for resolving the authenticated principal and tenant for the current request.
 *
 * <p>This contract is framework-agnostic on purpose so inbound adapters can depend on it
 * without knowing how authentication details are extracted.</p>
 */
public interface AuthenticatedTenantContext {

    /**
     * Returns the validated tenant identifier associated with the current authenticated request.
     *
     * @return authenticated tenant identifier
     */
    TenantId getCurrentTenantId();

    /**
     * Returns the authenticated principal identifier for the current request.
     *
     * @return authenticated principal identifier
     */
    String getCurrentPrincipalId();
}
