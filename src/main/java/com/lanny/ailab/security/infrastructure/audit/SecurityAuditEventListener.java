package com.lanny.ailab.security.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Writes structured audit events to the application log for operational tracing
 * and governance.
 */
@Component
public class SecurityAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditEventListener.class);

    /**
     * Logs the received event in a single structured line suitable for search and
     * correlation.
     *
     * @param event audit event emitted by the security layer or sensitive endpoint
     *              adapters
     */
    @EventListener
    public void onAuditEvent(SecurityAuditEvent event) {
        log.info(
                "SECURITY_AUDIT category={} action={} outcome={} tenantId={} principalId={} resourceType={} resourceId={} queryId={} httpMethod={} httpPath={} details={}",
                event.category(),
                event.action(),
                event.outcome(),
                safe(event.tenantId()),
                safe(event.principalId()),
                safe(event.resourceType()),
                safe(event.resourceId()),
                safe(event.queryId()),
                safe(event.httpMethod()),
                safe(event.httpPath()),
                event.details());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
