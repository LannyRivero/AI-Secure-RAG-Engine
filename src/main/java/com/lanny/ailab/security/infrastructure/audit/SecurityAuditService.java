package com.lanny.ailab.security.infrastructure.audit;

import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes structured audit and security events so governance signals stay
 * centralized.
 */
@Service
public class SecurityAuditService {

    private final ApplicationEventPublisher eventPublisher;

    public SecurityAuditService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publishes an audit event for a successful or rejected sensitive business
     * operation.
     *
     * @param action       logical action name
     * @param outcome      normalized outcome value
     * @param tenantId     affected tenant
     * @param principalId  authenticated principal performing the action
     * @param resourceType logical target resource type
     * @param resourceId   logical target resource identifier
     * @param details      additional metadata safe to include in logs
     */
    public void publishSensitiveOperation(
            String action,
            String outcome,
            TenantId tenantId,
            String principalId,
            String resourceType,
            String resourceId,
            Map<String, String> details) {

        publish(new SecurityAuditEvent(
                "audit",
                action,
                outcome,
                tenantId.value(),
                principalId,
                resourceType,
                resourceId,
                MDC.get("queryId"),
                MDC.get("httpMethod"),
                MDC.get("httpPath"),
                details));
    }

    /**
     * Publishes a security event for authentication or authorization outcomes.
     *
     * @param action      security action name
     * @param outcome     normalized outcome value
     * @param principalId authenticated principal involved, when known
     * @param tenantId    tenant involved, when known
     * @param details     additional metadata safe to include in logs
     */
    public void publishSecurityEvent(
            String action,
            String outcome,
            String principalId,
            String tenantId,
            Map<String, String> details) {

        publish(new SecurityAuditEvent(
                "security",
                action,
                outcome,
                tenantId,
                principalId,
                "http_request",
                MDC.get("httpPath"),
                MDC.get("queryId"),
                MDC.get("httpMethod"),
                MDC.get("httpPath"),
                details));
    }

    private void publish(SecurityAuditEvent event) {
        eventPublisher.publishEvent(event);
    }
}
