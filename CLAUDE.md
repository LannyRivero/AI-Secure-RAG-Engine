# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Secure RAG Engine — a production-ready Retrieval-Augmented Generation system with multi-tenancy, built with Spring Boot 3, Spring AI, PostgreSQL + pgvector, and Hexagonal Architecture.

## Build & Run Commands

```bash
# Build
./mvnw compile
./mvnw clean package

# Run application (requires Docker services and OPENAI_API_KEY)
docker compose up -d
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run

# Tests
./mvnw test                                          # All tests
./mvnw test -Dgroups="unit"                          # Unit tests only
./mvnw test -Dgroups="integration"                   # Integration tests (uses Testcontainers)
./mvnw test -Dgroups="acceptance"                    # Acceptance tests (requires OPENAI_API_KEY)
./mvnw test -Dtest=QueryRagServiceTest               # Single test class
./mvnw test -Dtest=QueryRagServiceTest#method_name   # Single test method
```

## Architecture

**Hexagonal Architecture (Ports & Adapters) + DDD.** Flow:

```
HTTP Request → Controller → UseCase (in-port) → Application Service → Output Port → Adapter
```

- `domain/` — Pure Java, zero framework dependencies. Value objects (`TenantId`, `DocumentId`, `SimilarityScore`), domain models (`Document`, `Chunk`), domain services (`ChunkingService`).
- `application/` — Use case interfaces (`port/in/`), output port interfaces (`port/out/`), application services that orchestrate ports, and policies (`RelevancePolicy` enforces 0.70 min similarity).
- `infrastructure/` — All framework/library code: Spring MVC controllers (`adapter/in/web/`), and outbound adapters (`adapter/out/`) for OpenAI, pgvector, and JPA.
- `security/` — OAuth2/JWT resource server. `TenantContext` extracts tenant from JWT claims. `KeycloakJwtAuthenticationConverter` maps roles to Spring Security authorities.
- `shared/` — `GlobalExceptionHandler` maps domain exceptions to HTTP responses.

## Key Design Rules

**Multi-tenancy:** `TenantId` value object validated at creation (`^[a-zA-Z0-9_-]{3,50}$`). All output ports accept `TenantId`, never `String`. SQL isolation enforced at every query (`WHERE tenant_id = ?`).

**Retrieval:** Switchable via `app.rag.retriever` property (`vector` | `hybrid`). Hybrid mode fuses pgvector HNSW semantic search with PostgreSQL full-text search (tsvector) using Reciprocal Rank Fusion (RRF, k=60).

**Resilience:** OpenAI calls wrapped with Resilience4j retry (3 attempts, exponential backoff) and circuit breaker (50% failure threshold, 30s open state). Rate limiting per tenant via Bucket4j.

**Prompt injection protection:** `PromptBuilder` strips control characters and caps user input at 2000 chars before building the grounded prompt.

## Configuration Profiles

| Profile | Purpose |
|---------|---------|
| *(default)* | Local dev, reads from `application.yaml` |
| `dev` | Local Docker (Postgres on port 5433) |
| `test` | Unit tests — in-memory H2, no Testcontainers |
| `integration-test` | Integration tests — Testcontainers pgvector |
| `prod` | All values from environment variables |

**Required env vars in prod:** `OPENAI_API_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`.

## Database Migrations (Flyway)

Migrations live in `src/main/resources/db/migration/`. Schema highlights:
- V1: `document_chunks` table with composite tenant/document indexes
- V2: HNSW vector index (m=16, ef_construction=64)
- V3: Embedding model metadata on documents
- V4: `content_tsv` (tsvector) column, GIN index, and trigger for full-text search (Spanish stemming)

## Test Organization

Tests are tagged `@Tag("unit")`, `@Tag("integration")`, or `@Tag("acceptance")`. Integration tests use Testcontainers (`pgvector/pgvector:pg16`) and require Docker. Acceptance tests use MockMvc with full Spring security context and require a real OpenAI key.

## Working Guidelines

### Language
- All code, comments, Javadoc, and documentation must be written in **English**.
- Responses and explanations in the terminal can be in Spanish.

### Before Making Any Change
Always explain what you are about to do and why before modifying any file. Include:
- What files will be affected
- Why this approach was chosen
- Any trade-offs or alternatives considered

### Architecture Rules (Non-Negotiable)
- Never place business logic in controllers or adapters — it belongs in application services.
- Never let `infrastructure/` classes leak into `domain/` or `application/` layers.
- Always use `TenantId` value object, never raw `String` for tenant identification.
- New output ports go in `application/port/out/`, new use cases in `application/port/in/`.
- Domain objects must have zero framework dependencies (`@Component`, `@Service`, etc. are forbidden in `domain/`).

### Tests
- Every new application service or domain service must have a corresponding unit test tagged `@Tag("unit")`.
- Every new output port adapter must have an integration test tagged `@Tag("integration")`.
- Tests follow the pattern: `given_<context>_when_<action>_then_<expected>`.

### Javadoc
- All public methods and public classes must have Javadoc.
- Javadoc must describe *what* the method does and *why* it exists, not just repeat the method name.
- Include `@param`, `@return`, and `@throws` where applicable.

### Response Style
- Always provide a short explanation of what was done and why after completing a task.
- If multiple approaches exist, mention the alternatives and justify the choice made.
- Flag any potential issue with existing code spotted while working, even if not asked.
