# AI Secure RAG Engine

> Motor de **Generación Aumentada por Recuperación (RAG)** multi-tenant listo para producción, construido con **Spring Boot 3, Spring AI, PostgreSQL + pgvector y Arquitectura Hexagonal**.

---

## Qué es esto

Un motor backend que permite a cualquier aplicación responder preguntas usando **únicamente su base de conocimientos**  garantizando:

- ❌ Sin alucinaciones del modelo
- 🔒 Sin filtración de datos entre organizaciones
- 📚 Respuestas siempre basadas en evidencia


El sistema ingesta documentos, los divide en fragmentos y genera embeddings, almacena los vectores en PostgreSQL y recupera el contexto semánticamente relevante antes de llamar al LLM. Si no existe contexto relevante, el sistema devuelve **`no_evidence`** en lugar de fabricar una respuesta.

Diseñado para integrarse en cualquier producto que necesite respuestas de IA fundamentadas sobre bases de conocimiento privadas.

---

## Capacidades principales

| Capacidad | Detalle |
|---|---|
| **Ingesta de documentos** | Chunking configurable con tamaño y overlap, upsert por documentId |
| **Recuperación semántica** | pgvector con índice HNSW, similitud coseno y topK configurable |
| **Respuestas fundamentadas** | El LLM solo responde con contexto recuperado; devuelve `no_evidence` si no hay evidencia |
| **Multi-tenancy** | Aislamiento estricto a nivel SQL — imposible el acceso cruzado entre tenants |
| **Autenticación** | JWT vía Keycloak con control de roles (`PLATFORM_ADMIN`, `ORG_MEMBER`) |
| **Auditoría y gobernanza** | Trazabilidad estructurada de ingest/query/delete y eventos `401/403` con correlación por `queryId` |
| **Rate limiting** | Token bucket por tenant con Bucket4j |
| **Protección contra prompt injection** | Sanitización de entrada antes de llamar al LLM |
| **Observabilidad** | Métricas con Micrometer: peticiones, llamadas LLM, `no_evidence`, rechazos |

---
## Por qué este proyecto es relevante

Muchos ejemplos de RAG en internet son simples demostraciones que no contemplan problemas reales de producción.

Este proyecto aborda desafíos que aparecen al construir sistemas de IA en entornos reales:

- aislamiento multi-tenant para evitar filtraciones de datos
- control de relevancia antes de llamar al LLM
- respuestas basadas exclusivamente en evidencia recuperada
- protección contra prompt injection
- estrategia de testing completa
- infraestructura real con PostgreSQL + pgvector

No es un tutorial, sino un **sistema backend diseñado con prácticas de producción.**

---

## Arquitectura

Hexagonal estricta (Ports & Adapters) con DDD. El dominio no tiene ninguna dependencia de Spring.

```
HTTP Request
    │
    ▼
[Controller]           ← infrastructure/adapter/in/web
    │  usa
    ▼
[Puerto UseCase]       ← application/port/in
    │  implementado por
    ▼
[Servicio Aplicación]  ← application/service
    │  llama a
    ▼
[Puertos Salida]       ← application/port/out
    │  implementados por
    ▼
[Adaptadores]          ← infrastructure/adapter/out
  ├── OpenAI (embeddings + chat)
  ├── pgvector (vector store + retrieval)
  └── PostgreSQL (repositorio de documentos)
```

**Decisiones de diseño clave:**

- La capa de dominio no tiene imports de framework — Java puro, testeable en aislamiento completo
- `TenantId` es un value object validado en construcción — los tenant IDs inválidos son rechazados antes de ejecutar cualquier lógica de negocio
- `ChunkingService` es un servicio de dominio instanciado sin Spring — tamaño de chunk y overlap se inyectan via configuración
- `RelevancePolicy` impone una puntuación mínima de similitud antes de llamar al LLM — evita que contexto de baja calidad llegue a OpenAI
- `PromptBuilder` sanitiza la entrada del usuario antes de inyectarla en el prompt — caracteres de control eliminados, longitud limitada, instrucción de guardia contra injection incluida

---

## Pipeline RAG

```
POST /rag/ingest
    │
    ├── Validar entrada (formato documentId, tamaño contenido)
    ├── Extraer tenantId del JWT
    ├── Eliminar chunks existentes para documentId+tenantId (upsert)
    ├── ChunkingService.chunk() → List<String> (512 palabras, overlap 50)
    └── Por cada chunk:
            EmbeddingPort.embed() → float[1536]
            VectorStorePort.store(tenantId, documentId, contenido, embedding)

POST /rag/query
    │
    ├── Validar entrada + verificar rate limit (20 req/min por tenant)
    ├── Extraer tenantId del JWT
    ├── EmbeddingPort.embed(query) → vector de consulta
    ├── RetrievalPort.retrieve(query, tenantId, topK) → chunks filtrados por tenant
    ├── RelevancePolicy.isRelevant(chunks) → verificación de umbral (defecto 0.70)
    ├── PromptBuilder.build(query, chunks) → prompt sanitizado
    ├── LlmChatPort.generateAnswer(prompt) → respuesta raw del LLM
    └── Devolver respuesta + fuentes de evidencia OR no_evidence

DELETE /rag/documents/{documentId}
    │
    ├── Extraer tenantId del JWT
    ├── Verificar existencia → 404 si no existe
    └── Eliminar todos los chunks para documentId+tenantId → 204
```

---

## Modelo de seguridad

| Aspecto | Implementación |
|---|---|
| Autenticación | Servidor de recursos OAuth2 JWT via Keycloak |
| Extracción de tenant | `TenantContext` lee el claim `attributes.tenant_id` del JWT |
| Validación de tenant | `TenantId.from()` valida el formato con regex — rechaza si es inválido |
| Aislamiento de tenant | Todas las queries SQL incluyen `WHERE tenant_id = ?` — aplicado en el adaptador |
| Control de roles | `PLATFORM_ADMIN` para ingest/delete/métricas, `ORG_MEMBER` para query |
| Actuator | `/actuator/prometheus` es opt-in (`app.security.public-prometheus.enabled`); resto de `/actuator/**` restringido a `PLATFORM_ADMIN` |
| Prompt injection | Caracteres de control eliminados, saltos de línea colapsados, longitud limitada a 2000 chars |
| Rate limiting | Token bucket in-memory por tenant con Bucket4j — configurable por operación |
| Auditoría | Logs `SECURITY_AUDIT` para operaciones sensibles y rechazos de autenticación/autorización |

---

## Stack tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.5 |
| Integración IA | Spring AI | 1.1.2 |
| Proveedor LLM | OpenAI | gpt-4o-mini |
| Vector Store | pgvector | pg16 |
| Base de datos | PostgreSQL | 16 |
| Migraciones | Flyway | 11 |
| Autenticación | Keycloak | 24 |
| Rate Limiting | Bucket4j | 8.10 |
| Observabilidad | Micrometer + Actuator + OpenTelemetry tracing | `docs/observability.md` |
| Testing | JUnit 5 + Mockito + Testcontainers | — |
| Build | Maven | — |
| Contenedores | Docker Compose | — |

---

## Estrategia de testing

Pirámide completa — sin infraestructura mockeada en los tests de integración.

| Capa | Tipo | Qué valida |
|---|---|---|
| Dominio | Unit | `TenantId`, `SimilarityScore`, `ChunkingService` — lógica pura, sin Spring |
| Aplicación | Unit | `QueryRagService`, `IngestDocumentService`, `DeleteDocumentService`, `RelevancePolicy`, `PromptBuilder` — puertos mockeados con Mockito |
| Infraestructura | Integración | `PgVectorRetriever`, `PgDocumentRepository` — PostgreSQL real via Testcontainers |
| Web | Aceptación | `RagController`, `IngestController`, `DeleteController` — stack HTTP completo, seguridad real, MockMvc |

Los tests de integración usan el contenedor `pgvector/pgvector:pg16` — sin base de datos mockeada, sin H2.
Los tests de aceptación validan autenticación (401/403), respuestas de negocio (200/201/204/404), manejo de errores (400/429/502) y aislamiento de tenant.

---

## Local Development

### Prerequisites

- Docker
- Java 21
- An OpenAI API key

Maven is not required locally because the repository ships the Maven Wrapper.

### 1. Create the local environment file

Copy `.env.example` to `.env` and fill in the values before starting Docker Compose.

```bash
cp .env.example .env
```

PowerShell alternative:

```powershell
Copy-Item .env.example .env
```

The `.env.example` file documents the full local contract. Docker Compose consumes the database and Keycloak variables from `.env`; the Spring Boot process still requires `OPENAI_API_KEY` to be exported in the shell before startup.

Local bootstrap variables:

| Variable | Purpose |
|---|---|
| `POSTGRES_USER` | Local pgvector PostgreSQL username |
| `POSTGRES_PASSWORD` | Local pgvector PostgreSQL password |
| `POSTGRES_DB` | Local pgvector PostgreSQL database |
| `KC_DB_NAME` | Keycloak PostgreSQL database |
| `KC_DB_USERNAME` | Keycloak PostgreSQL username |
| `KC_DB_PASSWORD` | Keycloak PostgreSQL password |
| `KEYCLOAK_ADMIN` | Local Keycloak admin user |
| `KEYCLOAK_ADMIN_PASSWORD` | Local Keycloak admin password |
| `KC_CLIENT_SECRET` | Secret injected into the imported `rag-engine` client |
| `REDIS_PORT` | Local Redis port used by distributed rate limiting |
| `KEYCLOAK_ISSUER_URI` | Local JWT issuer used by Spring Security |
| `OPENAI_API_KEY` | OpenAI API key required when starting the Spring Boot app |

### 2. Start infrastructure

```bash
docker compose up -d
```

The local stack exposes:

- pgvector PostgreSQL on `localhost:5433`
- Keycloak on `http://localhost:8180`
- Redis on `localhost:6379`

### 3. Wait for readiness in the correct order

The compose file defines healthchecks for `db`, `keycloak-db`, `keycloak`, and `redis`. Wait until all four services report `healthy`:

```bash
docker compose ps
```

Container startup alone is not enough. `keycloak` becomes `healthy` only when the realm OIDC metadata endpoint responds successfully, which is the same signal the Spring Boot app depends on to initialize JWT validation.

Expected health states:

- `db` → `healthy`
- `keycloak-db` → `healthy`
- `keycloak` → `healthy`
- `redis` → `healthy`

If you want to inspect the realm manually, this endpoint should also return metadata once Keycloak is ready:

```bash
curl http://localhost:8180/realms/rag-engine/.well-known/openid-configuration
```

If that endpoint does not return realm metadata yet, wait and try again. Starting Spring Boot too early causes JWT decoder initialization to fail because the issuer is not ready.

### 4. Start the application with the `dev` profile

Export `OPENAI_API_KEY` in the same shell that will launch Spring Boot. The datasource and issuer values already have local defaults, but the OpenAI key does not.

Unix-like shells:

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows PowerShell:

```powershell
$env:OPENAI_API_KEY="sk-..."
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway runs automatically during startup.

### 5. Smoke-check the local flow

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI docs: `http://localhost:8080/v3/api-docs`
- Keycloak realm: `http://localhost:8180/realms/rag-engine`

Estas superficies existen en `dev` porque se habilitan explícitamente ahí. El default global ahora es cerrado.

Imported demo users from `keycloak/realm-export.json`:

| Username | Password | Role |
|---|---|---|
| `admin-test` | `password` | `PLATFORM_ADMIN` |
| `tecnica-test` | `password` | `ORG_MEMBER` |

The imported OAuth client is `rag-engine` and its local secret must match `KC_CLIENT_SECRET`.

### 6. Distributed rate limiting notes

The `dev` and `prod` profiles use a Redis-backed Bucket4j rate limiter so tenant quotas stay consistent across multiple instances.

### 7. Optional local monitoring stack

To validate the observability branch end to end, start the monitoring stack in a separate compose project:

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

Then start Spring Boot with tracing export enabled:

```powershell
$env:MANAGEMENT_TRACING_EXPORT_ENABLED="true"
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://localhost:4318/v1/traces"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Available local endpoints:

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Tempo: `http://localhost:3200`
- OTLP collector: `http://localhost:4318/v1/traces`
- Alert webhook sink: `http://localhost:18080`

The stack provisions the repo dashboard automatically, loads the Prometheus alert rules from `monitoring/prometheus/rules/`, stores traces in Tempo, and routes alerts through Alertmanager to a local webhook sink for validation.

Trade-offs:

- Pros: one shared quota per tenant across pods, no per-pod quota multiplication, no reset when a single pod restarts.
- Cons: the application now depends on Redis availability for rate-limit checks.
- Failure mode: the system is intentionally **fail-closed**. If Redis is temporarily unavailable, the API returns `503 Service Unavailable` instead of silently falling back to per-instance buckets.
- Why fail-closed: falling back to local memory would make quotas inconsistent between pods and would break the main guarantee of this branch.

---

## Referencia de API

### Ingestar un documento

```http
POST /rag/ingest
Authorization: Bearer <jwt>   # requiere rol PLATFORM_ADMIN
Content-Type: application/json

{
  "documentId": "doc-001",
  "content": "El texto de tu documento aquí..."
}
```

```json
{
  "documentId": "doc-001",
  "chunksIndexed": 4
}
```

### Consultar

```http
POST /rag/query
Authorization: Bearer <jwt>   # requiere rol ORG_MEMBER o PLATFORM_ADMIN
Content-Type: application/json

{
  "query": "¿Cuáles son las características principales?",
  "topK": 5
}
```

```json
{
  "answer": "Basándome en los documentos indexados...",
  "hasEvidence": true,
  "evidence": [
    { "documentId": "doc-001", "score": 0.97 }
  ]
}
```

### Eliminar un documento

```http
DELETE /rag/documents/{documentId}
Authorization: Bearer <jwt>   # requiere rol PLATFORM_ADMIN
```

Devuelve `204 No Content` si se elimina correctamente, `404 Not Found` si el documento no existe.

---

## Configuración

Propiedades clave en `application.yaml`:

```yaml
app:
  swagger:
    enabled: false
  security:
    public-prometheus:
      enabled: false
  llm:
    provider: openai        # stub | openai
  rag:
    min-score-threshold: 0.70
    default-top-k: 3
    max-top-k: 20
    rate-limit:
      query-requests-per-minute: 20
      ingest-requests-per-minute: 10
```

---

## Operación de seguridad

- Guía operativa: `docs/security-operations.md`
- Observabilidad correlacionada: `docs/observability.md`

El punto importante es este: autenticación sin trazabilidad no alcanza en entornos enterprise. Esta rama deja auditadas las operaciones sensibles sin loggear payloads ni secretos.

---

## Despliegue en producción

Todos los valores sensibles se inyectan via variables de entorno. La aplicación falla en el arranque si alguna variable requerida no está presente.

| Variable | Descripción |
|---|---|
| `OPENAI_API_KEY` | API key de OpenAI |
| `DB_URL` | URL JDBC — ej. `jdbc:postgresql://host:5432/rag_engine` |
| `DB_USERNAME` | Usuario de base de datos |
| `DB_PASSWORD` | Contraseña de base de datos |
| `REDIS_HOST` | Host de Redis para cuotas distribuidas |
| `REDIS_PORT` | Puerto de Redis — por defecto `6379` |
| `REDIS_PASSWORD` | Contraseña de Redis si el entorno la requiere |
| `KEYCLOAK_ISSUER_URI` | URI del realm de Keycloak |

Ejecutar con perfil de producción:

```bash
java -jar target/ai-secure-rag-engine.jar --spring.profiles.active=prod
```

---

## Autora

**Lanny Rivero**
Desarrolladora Backend — Java · Spring Boot · Spring AI · Sistemas Distribuidos
