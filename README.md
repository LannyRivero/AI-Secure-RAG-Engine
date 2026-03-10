# AI Secure RAG Engine

> Production-ready **Retrieval-Augmented Generation (RAG)** engine with multi-tenancy, built with **Spring Boot 3, Spring AI, PostgreSQL + pgvector and Hexagonal Architecture**.

---

## What is this

A backend engine that enables any application to answer questions using **only its own knowledge base**, guaranteeing:

- ❌ No model hallucinations
- 🔒 No data leakage between organisations
- 📚 Every response backed by retrieved evidence
- ⚡ Graceful degradation when the LLM provider is unavailable

The system ingests documents, splits them into chunks, generates embeddings, stores vectors in PostgreSQL and retrieves semantically relevant context before calling the LLM. If no relevant context exists, the system returns **`no_evidence`** instead of fabricating a response.

Designed to be embedded in any product that needs grounded AI responses over private knowledge bases.

---

## Core capabilities

| Capability | Detail |
|---|---|
| **Document ingestion** | Configurable chunking with size and overlap, upsert by documentId |
| **Hybrid retrieval** | Vector search (pgvector HNSW) + full-text search (PostgreSQL tsvector), merged via Reciprocal Rank Fusion (RRF) |
| **Grounded responses** | LLM only answers with retrieved context; returns `no_evidence` if evidence is insufficient |
| **Multi-tenancy** | Strict SQL-level isolation — cross-tenant access is structurally impossible |
| **Authentication** | JWT via Keycloak with role-based access control (`PLATFORM_ADMIN`, `ORG_MEMBER`) |
| **Rate limiting** | Per-tenant token bucket with Bucket4j, configurable per operation |
| **LLM resilience** | Retry (3 attempts, exponential backoff) + Circuit Breaker (50% threshold, 30s open state) via Resilience4j |
| **Prompt injection protection** | Input sanitisation before calling the LLM — control characters removed, length capped, guard instruction injected |
| **Observability** | Latency histograms (p50/p95/p99) for retrieval and LLM calls, per-request queryId tracing via MDC, Prometheus metrics |

---

## Why this project matters

Most RAG examples online are simple demos that ignore real production concerns.

This project addresses challenges that appear when building AI systems in real environments:

- Multi-tenant isolation to prevent data leakage
- Relevance control before calling the LLM
- Responses based exclusively on retrieved evidence
- Protection against prompt injection
- LLM gateway resilience with retry and circuit breaker
- Hybrid search combining semantic and keyword retrieval
- Complete testing strategy with real infrastructure
- Structured observability for production debugging

This is not a tutorial — it is a **backend system designed with production-grade practices**.

---

## Architecture

Strict Hexagonal Architecture (Ports & Adapters) with DDD. The domain layer has zero framework dependencies.

```
HTTP Request
    │
    ▼
[Controller]              ← infrastructure/adapter/in/web
    │  uses
    ▼
[UseCase Port]            ← application/port/in
    │  implemented by
    ▼
[Application Service]     ← application/service
    │  calls
    ▼
[Output Ports]            ← application/port/out
    │  implemented by
    ▼
[Adapters]                ← infrastructure/adapter/out
  ├── OpenAI              (embeddings + chat, with Resilience4j)
  ├── HybridRetriever     (pgvector ANN + PostgreSQL full-text, RRF fusion)
  └── PgDocumentRepository (document chunk management)
```

**Key design decisions:**

- The domain layer has no Spring imports — pure Java, fully testable in isolation
- `TenantId` is a validated value object — invalid tenant IDs are rejected before any business logic executes
- `RetrievalPort` accepts `TenantId`, not `String` — tenant type safety enforced at compile time across all output ports
- `RelevancePolicy` enforces a minimum similarity score before calling the LLM — low-quality context never reaches OpenAI
- `PromptBuilder` sanitises user input before injecting it into the prompt — control characters removed, length capped at 2000 chars, injection guard instruction included
- `HybridRetriever` executes vector search and full-text search independently, then merges results via RRF (k=60) — chunks appearing in both result sets rank higher
- `RetrieverConfig` registers the active retriever via `@ConditionalOnProperty` — switchable between `vector` and `hybrid` without code changes

---

## RAG Pipeline

```
POST /rag/ingest
    │
    ├── Validate input (documentId format, content size)
    ├── Extract tenantId from JWT
    ├── Delete existing chunks for documentId+tenantId (upsert semantics)
    ├── ChunkingService.chunk() → List<String> (512 words, overlap 50)
    └── For each chunk:
            EmbeddingPort.embed() → float[1536]
            VectorStorePort.store(tenantId, documentId, content, embedding)

POST /rag/query
    │
    ├── Validate input + check rate limit (20 req/min per tenant)
    ├── Extract tenantId from JWT
    ├── HybridRetriever.retrieve(query, tenantId, topK)
    │     ├── Vector search  → top 2*topK by cosine similarity
    │     ├── Full-text search → top 2*topK by ts_rank
    │     └── RRF fusion → ranked List<DocumentChunk>
    ├── RelevancePolicy.isRelevant(chunks) → threshold check
    ├── PromptBuilder.build(query, chunks) → sanitised prompt
    ├── LlmChatPort.generateAnswer(prompt)
    │     ├── @Retry: up to 3 attempts with exponential backoff
    │     └── @CircuitBreaker: opens at 50% failure rate
    └── Return answer + evidence sources OR no_evidence

DELETE /rag/documents/{documentId}
    │
    ├── Extract tenantId from JWT
    ├── Check existence → 404 if not found
    └── Delete all chunks for documentId+tenantId → 204
```

---

## Security model

| Aspect | Implementation |
|---|---|
| Authentication | OAuth2 JWT resource server via Keycloak |
| Tenant extraction | `TenantContext` reads `attributes.tenant_id` claim from JWT |
| Tenant validation | `TenantId.from()` validates format with regex — rejects invalid values |
| Tenant isolation | All SQL queries include `WHERE tenant_id = ?` — enforced at adapter level |
| Role-based access | `PLATFORM_ADMIN` for ingest/delete/metrics, `ORG_MEMBER` for query |
| Actuator endpoints | Restricted to `PLATFORM_ADMIN` |
| Prompt injection | Control characters stripped, newlines collapsed, length capped, guard instruction injected |
| Rate limiting | In-memory token bucket per tenant with Bucket4j — configurable per operation |

---

## Resilience

The LLM gateway is decorated with Resilience4j to handle provider failures gracefully:

| Mechanism | Configuration |
|---|---|
| **Retry** | 3 attempts, 500ms initial wait, exponential backoff (×2) |
| **Circuit Breaker** | COUNT_BASED, 10-call window, opens at 50% failure rate, 30s open state |
| **Timeout** | 5s connection timeout, 10s read timeout via Spring AI config |

When retries are exhausted or the circuit is open, the system throws `LlmProviderException` which the global exception handler maps to `502 Bad Gateway`.

---

## Observability

Every request is assigned a `queryId` (UUID) via MDC. All log lines within the request include this identifier automatically.

```
10:23:41.123 [http-nio] [a3f2b1c4-...] INFO QueryRagService - RAG_QUERY_COMPLETE tenantId=org-test topK=3 chunksRetrieved=2 hasEvidence=true
```

The `queryId` is also returned in the `X-Query-Id` response header for client-side correlation.

**Prometheus metrics exposed at `/actuator/prometheus`:**

| Metric | Description |
|---|---|
| `rag.requests.total` | Total RAG queries received |
| `rag.responses.no_evidence` | Queries returning no evidence |
| `rag.requests.threshold_rejected` | Queries rejected by relevance policy |
| `rag.llm.calls` | Total LLM provider calls |
| `rag.retrieval.latency` | Vector + full-text retrieval latency (p50/p95/p99) |
| `rag.llm.latency` | LLM response latency (p50/p95/p99) |

---

## Testing strategy

Full pyramid — no mocked infrastructure in integration tests.

| Layer | Type | What it validates |
|---|---|---|
| Domain | Unit | `TenantId`, `SimilarityScore`, `ChunkingService` — pure Java, no Spring |
| Application | Unit | `QueryRagService`, `IngestDocumentService`, `DeleteDocumentService`, `RelevancePolicy`, `PromptBuilder` — ports mocked with Mockito |
| Infrastructure | Integration | `PgVectorRetriever`, `HybridRetriever`, `PgDocumentRepository` — real PostgreSQL via Testcontainers |
| Pipeline | Integration | End-to-end ingest → query with real PostgreSQL — cross-tenant isolation verified |
| Web | Acceptance | `RagController`, `IngestController`, `DeleteController` — full HTTP stack, real security config, MockMvc |

Integration tests use the `pgvector/pgvector:pg16` container — no mocked database, no H2.

Acceptance tests cover: authentication (401/403), business responses (200/201/204/404), error handling (400/429/502) and tenant isolation.

---

## Tech stack

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.5 |
| AI Integration | Spring AI | 1.1.2 |
| LLM Provider | OpenAI | gpt-4o-mini |
| Vector Store | pgvector | pg16 |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | 11 |
| Authentication | Keycloak | 24 |
| Resilience | Resilience4j | 2.2 |
| Rate Limiting | Bucket4j | 8.10 |
| Observability | Micrometer + Actuator + Prometheus | — |
| Testing | JUnit 5 + Mockito + Testcontainers | — |
| Build | Maven | — |
| Containers | Docker Compose | — |

---

## Local setup

### Prerequisites

- Docker
- Java 21
- Maven
- OpenAI API key

### 1. Start infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL with pgvector on port `5433` and Keycloak on port `8180`.
Wait for both containers to reach `healthy` status before starting the application.

### 2. Set environment variables

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Run

```bash
mvn spring-boot:run
```

Flyway runs all migrations automatically on startup.

---

## API reference

### Ingest a document

```http
POST /rag/ingest
Authorization: Bearer <jwt>   # requires PLATFORM_ADMIN role
Content-Type: application/json

{
  "documentId": "doc-001",
  "content": "Your document text here..."
}
```

```json
{
  "documentId": "doc-001",
  "chunksIndexed": 4
}
```

### Query

```http
POST /rag/query
Authorization: Bearer <jwt>   # requires ORG_MEMBER or PLATFORM_ADMIN role
Content-Type: application/json

{
  "query": "What are the main features?",
  "topK": 5
}
```

```json
{
  "answer": "Based on the indexed documents...",
  "hasEvidence": true,
  "evidence": [
    { "documentId": "doc-001", "score": 0.97 }
  ]
}
```

### Delete a document

```http
DELETE /rag/documents/{documentId}
Authorization: Bearer <jwt>   # requires PLATFORM_ADMIN role
```

Returns `204 No Content` on success, `404 Not Found` if the document does not exist.

---

## Configuration

Key properties in `application.yaml`:

```yaml
app:
  llm:
    provider: openai        # stub | openai
  rag:
    retriever: hybrid       # vector | hybrid
    min-score-threshold: 0.70
    default-top-k: 3
    max-top-k: 20
    rate-limit:
      query-requests-per-minute: 20
      ingest-requests-per-minute: 10

resilience4j:
  retry:
    instances:
      llmRetry:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
  circuitbreaker:
    instances:
      llmCircuitBreaker:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

---

## Production deployment

All sensitive values are injected via environment variables. The application fails on startup if any required variable is missing.

| Variable | Description |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key |
| `DB_URL` | JDBC URL — e.g. `jdbc:postgresql://host:5432/rag_engine` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `KEYCLOAK_ISSUER_URI` | Keycloak realm URI |

Run with production profile:

```bash
java -jar target/ai-secure-rag-engine.jar --spring.profiles.active=prod
```

---

## Release history

| Version | What changed |
|---|---|
| v1.0.0 | Initial release — hexagonal architecture, multi-tenancy, JWT security, full test pyramid |
| v1.1.0 | `TenantId` strong typing across all output ports, end-to-end pipeline integration test, technical debt cleanup |
| v1.2.0 | Resilience4j on LLM gateway, latency histograms, queryId tracing, structured logging |
| v1.3.0 | Hybrid search — RRF fusion of vector search and full-text search, V4 migration |

---

## Author

**Lanny Rivero**
Backend Developer — Java · Spring Boot · Spring AI · Distributed Systems

---

## License

MIT License — see [LICENSE](LICENSE) for details.