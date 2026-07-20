# Production Deployment Guide

This service is intended to run in production as a Spring Boot application with external PostgreSQL, Redis, Keycloak, and OpenAI dependencies. The `prod` profile is intentionally strict: Swagger is disabled, public Prometheus exposure is closed by default, distributed rate limiting is enabled, and required infrastructure values must be injected from the runtime platform.

## Quick path

1. Build the application artifact with `./mvnw clean package`.
2. Provide the required production environment variables for PostgreSQL, Redis, Keycloak, and OpenAI.
3. Run the jar with `--spring.profiles.active=prod`.
4. Verify startup through `/actuator/health` and confirm authenticated RAG traffic succeeds.

## Environment strategy

This repository distinguishes between a public validation environment and real production.

| Environment | Purpose | Reality in this repo |
|---|---|---|
| `dev` | Local development and day-to-day coding | Docker Compose on the developer machine |
| `staging` | Public validation, demos, and pre-production checks | Best fit for a free-tier deployment |
| `prod` | Real user traffic, real operational ownership, stable dependencies | Documented as the target operating model |

That distinction matters. A service deployed on a free platform is usually staging, not production, because suspend policies, shared quotas, and limited infrastructure guarantees make it unsuitable as a serious production commitment.

## Runtime model

| Component | Responsibility |
|---|---|
| Spring Boot app | API, retrieval orchestration, security enforcement, Flyway migrations |
| PostgreSQL + pgvector | Document metadata, chunk storage, vector search, full-text search |
| Redis | Shared rate-limit backend across instances |
| Keycloak | JWT issuer and role source |
| OpenAI | Embeddings and grounded answer generation |

The production story matters here: the application is stateless, tenant-aware, and safe to scale horizontally only because Redis centralizes quotas and PostgreSQL remains the source of truth for retrieval data.

## Required environment variables

The following variables are required for a real production boot path.

| Variable | Required | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | Yes | OpenAI credential used by Spring AI |
| `DB_URL` | Yes | JDBC URL for the application PostgreSQL database |
| `DB_USERNAME` | Yes | PostgreSQL username |
| `DB_PASSWORD` | Yes | PostgreSQL password |
| `REDIS_HOST` | Yes | Redis host for distributed rate limiting |
| `REDIS_PORT` | No | Redis port, defaults to `6379` |
| `REDIS_PASSWORD` | No | Redis password when the environment requires it |
| `KEYCLOAK_ISSUER_URI` | Yes | Keycloak realm issuer used by the JWT resource server |
| `APP_SECURITY_PUBLIC_PROMETHEUS_ENABLED` | No | Set to `true` only when unauthenticated scraping is explicitly intended |
| `MANAGEMENT_TRACING_EXPORT_ENABLED` | No | Enables OTLP trace export |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | No | OTLP HTTP endpoint for trace export |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | No | Trace sampling override, defaults to `1.0` |

Example contract:

```bash
export OPENAI_API_KEY=sk-...
export DB_URL=jdbc:postgresql://postgres.internal:5432/rag_engine
export DB_USERNAME=rag_engine
export DB_PASSWORD=change-me
export REDIS_HOST=redis.internal
export REDIS_PORT=6379
export KEYCLOAK_ISSUER_URI=https://sso.example.com/realms/rag-engine
export MANAGEMENT_TRACING_EXPORT_ENABLED=true
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector.observability:4318/v1/traces
```

## Profile strategy

| Profile | Where it lives | Intent |
|---|---|---|
| default | `src/main/resources/application.yaml` | Shared defaults, secure-by-default exposure model, OpenAI integration enabled |
| `dev` | `src/main/resources/application-dev.yml` | Local Docker workflow, Redis-backed rate limiting, Swagger enabled, public Prometheus enabled |
| `prod` | `src/main/resources/application-prod.yml` | External infrastructure only, reduced logging noise, Redis-backed rate limiting, Swagger disabled |
| `test` | `src/main/resources/application-test.yml` | Fast unit-test support |
| `integration-test` | `src/test/resources/application-integration-test.yml` | Testcontainers-backed integration tests with stub LLM |

Production should use only `prod`. Do not overload `dev` with environment variables and call that production-like. That hides real differences in exposure, logging, and dependency wiring.

The repository does not define a dedicated Spring `staging` profile today. If you publish a free public demo, use the `prod` profile with staging-scoped infrastructure and secrets so the runtime behavior stays close to the real target environment.

## Free-tier staging recommendation

If the goal is to prove the system runs outside localhost at zero cost, the clean story is:

1. Use `dev` locally for development.
2. Deploy one public `staging` instance on a free platform.
3. Keep `prod` as the documented target operating model.

Recommended positioning for this repo:

- `staging` is the publicly accessible demo or validation environment.
- `production` is the serious operating model described in this guide.

This is more honest than calling a free sandbox production.

## Platform notes

### Render

Render is a reasonable free-tier staging candidate for the application service because it supports free web services, but the free tier has usage limits and is not a strong production foundation.

What to keep in mind:

- free services consume a monthly free-hours quota
- free usage also has bandwidth and build-pipeline limits
- free Postgres instances are temporary on Render and expire after 30 days
- this project still needs PostgreSQL with `pgvector`, Keycloak, and OpenAI configuration even when Redis is provisioned on Render

That means a fully free end-to-end deployment may require compromises, reduced uptime expectations, or external managed services.

This repository now includes:

- `Dockerfile` for containerized deployment
- `render.yaml` for a free-tier public staging service on Render
- `/healthz` as a minimal unauthenticated platform health endpoint

Important constraint: the provided `render.yaml` is intentionally scoped to staging. It wires the application service and a free Render Key Value instance, but it still expects you to provide:

- a PostgreSQL database with `pgvector` enabled
- a valid `KEYCLOAK_ISSUER_URI`
- an `OPENAI_API_KEY`

That is the honest trade-off. The app can be published publicly for free, but the full enterprise dependency graph is not realistically production-grade on zero-cost infrastructure.

### GitHub Actions

GitHub Actions is CI/CD automation, not hosting.

Use it to:

- run tests
- build the jar or Docker image
- publish artifacts
- trigger a deploy to Render or another platform

Do not treat it as the place where the backend stays running. It does not provide the persistent runtime for this application.

## Deployment procedure

### 1. Build the artifact

```bash
./mvnw clean package
```

Primary artifact:

- `target/ai-secure-rag-engine.jar`

### 2. Provision dependencies

Before the first rollout, ensure:

- PostgreSQL already has the `vector` extension available.
- Redis is reachable from every app instance.
- Keycloak exposes the configured realm issuer URL.
- Network policy allows the app to reach PostgreSQL, Redis, Keycloak, and OpenAI.

### 3. Inject configuration and secrets

Use the runtime platform secret manager or environment injection. Do not patch secrets into `application-prod.yml` or commit environment-specific values into the repository.

### 4. Start the application

```bash
java -jar target/ai-secure-rag-engine.jar --spring.profiles.active=prod
```

What happens on startup:

1. Spring Boot loads shared defaults from `application.yaml`.
2. The `prod` profile overrides datasource, Redis, logging, and exposure settings.
3. Flyway validates and applies database migrations.
4. The JWT resource server initializes against `KEYCLOAK_ISSUER_URI`.
5. The application starts accepting traffic only after the web server is ready.

## Post-deploy verification

Run this short verification after every rollout:

1. Check `/actuator/health` with an admin token and confirm the application reports `UP`.
2. Confirm logs do not show Flyway failures, JWT issuer resolution failures, Redis connectivity errors, or OpenAI configuration errors.
3. Execute one authenticated `POST /rag/query` request for a tenant with known indexed data.
4. Confirm `/actuator/prometheus` exposure matches the intended environment policy.

Expected operational behavior:

- Swagger remains disabled in `prod`.
- Distributed rate limiting uses Redis, not local memory.
- The app fails closed if Redis is unavailable during quota checks.
- The app fails fast on missing required production variables.

## Basic runbook

### Health checks

- Liveness/readiness surface: `/actuator/health`
- Metrics surface: `/actuator/prometheus`
- Security audit signal: search for `SECURITY_AUDIT` in logs

### Common failure modes

| Symptom | Likely cause | First check |
|---|---|---|
| Boot fails before serving traffic | Missing env var or invalid datasource/JWT issuer config | Startup logs for unresolved placeholders, datasource errors, or JWT issuer failures |
| `503 Service Unavailable` on RAG endpoints | Redis unavailable while using distributed rate limits | Redis reachability and `rag_rate_limit_backend_unavailable_total` |
| `401` on every authenticated call | Wrong `KEYCLOAK_ISSUER_URI` or issuer unreachable | Realm metadata endpoint and JWT issuer configuration |
| Query latency spike | Retrieval, Redis, or OpenAI slowdown | `docs/observability.md` dashboards and traces |
| No answers with evidence | Retrieval threshold too strict or documents not indexed for the tenant | Ingest history, tenant isolation, retrieval metrics |

### Rollback guidance

If a release is unhealthy:

1. Stop routing traffic to the new instance set.
2. Restore the previous application artifact or image.
3. Re-run the post-deploy verification.
4. Review startup logs, traces, and audit events before retrying rollout.

Rollback is safer than hot-editing production configuration on the instance. Preserve the runtime contract and fix the artifact or platform config at the source.

## Related guides

- `docs/security-operations.md`
- `docs/observability.md`
