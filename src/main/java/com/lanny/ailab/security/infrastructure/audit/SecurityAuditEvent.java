package com.lanny.ailab.security.infrastructure.audit;

import java.util.Map;

/**
 * Immutable audit event emitted for sensitive operations and security-relevant
 * request outcomes.
 *
 * @param category     event family such as {@code audit} or {@code security}
 * @param action       logical action being recorded
 * @param outcome      normalized outcome of the action
 * @param tenantId     tenant involved in the event, when known
 * @param principalId  authenticated subject involved in the event, when known
 * @param resourceType logical resource type targeted by the action
 * @param resourceId   logical resource identifier targeted by the action
 * @param queryId      correlation identifier propagated through the request
 * @param httpMethod   originating HTTP method, when available
 * @param httpPath     originating HTTP path, when available
 * @param details      additional low-volume metadata safe to log
 */
public record SecurityAuditEvent(
                String category,
                String action,
                String outcome,
                String tenantId,
                String principalId,
                String resourceType,
                String resourceId,
                String queryId,
                String httpMethod,
                String httpPath,
                Map<String, String> details) {
}
