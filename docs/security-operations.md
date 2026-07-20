# Security Operations Guide

This service now treats authentication, endpoint exposure, and sensitive-operation tracing as explicit operational concerns instead of implicit defaults.

## What is audited

The application emits structured `SECURITY_AUDIT` log entries for:

- `rag.ingest`
- `rag.query`
- `rag.delete`
- unauthenticated requests rejected with `401`
- authenticated requests rejected with `403`

Each audit line includes request correlation (`queryId`), HTTP method/path, principal when available, tenant when available, and safe metadata such as `documentId`, `topK`, or evidence count.

Raw query text, raw document content, JWTs, and secrets are intentionally never logged.

## Default exposure model

Security-sensitive defaults are closed by default:

- `app.swagger.enabled=false`
- `app.security.public-prometheus.enabled=false`
- `management.endpoints.web.exposure.include=health,prometheus`

That matters. Enterprise posture is not just about adding auth annotations. It is about making exposure an explicit decision per environment.

## Environment variables and secrets

Required in production:

- `OPENAI_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `KEYCLOAK_ISSUER_URI`

Optional but security-relevant:

- `REDIS_PORT`
- `REDIS_PASSWORD`
- `APP_SECURITY_PUBLIC_PROMETHEUS_ENABLED`
- `MANAGEMENT_TRACING_EXPORT_ENABLED`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`

Rules for operators:

1. Inject secrets through the runtime platform secret manager or environment variables, never by committing them into `application*.yml`.
2. Keep `APP_SECURITY_PUBLIC_PROMETHEUS_ENABLED=false` unless Prometheus scraping is constrained by network policy or service mesh controls.
3. Enable Swagger only in explicitly trusted environments.
4. Rotate `OPENAI_API_KEY`, Keycloak client secrets, and Redis credentials through the platform secret workflow, not by patching repository config.
5. Treat audit logs as sensitive operational data because they include tenant and principal identifiers.

## Dev and production intent

- `dev`: Swagger and unauthenticated Prometheus scraping are enabled explicitly for local workflow convenience.
- `prod`: Swagger stays disabled and public Prometheus exposure stays disabled unless an operator opts in.

## Audit trail usage

Search for `SECURITY_AUDIT` in your log backend and pivot on:

- `queryId` for one request
- `tenantId` for tenant-scoped incident review
- `principalId` for user/admin activity tracing
- `action` + `outcome` for governance reporting

That gives you traceability without leaking business payloads.
