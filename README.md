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
    ├── RelevancePolicy.isRelevant(chunks) → verificación de umbral (defecto 0.95)
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
| Actuator | Restringido a `PLATFORM_ADMIN` |
| Prompt injection | Caracteres de control eliminados, saltos de línea colapsados, longitud limitada a 2000 chars |
| Rate limiting | Token bucket in-memory por tenant con Bucket4j — configurable por operación |

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
| Observabilidad | Micrometer + Actuator | — |
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

## Ejecución local

### Requisitos previos

- Docker
- Java 21
- Maven
- API key de OpenAI

### 1. Levantar infraestructura

```bash
docker compose up -d
```

Inicia PostgreSQL con pgvector en el puerto `5433` y Keycloak en el puerto `8180`.
Espera a que ambos contenedores estén en estado `healthy` antes de arrancar la aplicación.

### 2. Configurar variables de entorno

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Arrancar

```bash
mvn spring-boot:run
```

Flyway ejecuta las migraciones automáticamente al arrancar.

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
  llm:
    provider: openai        # stub | openai
  rag:
    min-score-threshold: 0.95
    default-top-k: 3
    max-top-k: 20
    rate-limit:
      query-requests-per-minute: 20
      ingest-requests-per-minute: 10
```

---

## Despliegue en producción

Todos los valores sensibles se inyectan via variables de entorno. La aplicación falla en el arranque si alguna variable requerida no está presente.

| Variable | Descripción |
|---|---|
| `OPENAI_API_KEY` | API key de OpenAI |
| `DB_URL` | URL JDBC — ej. `jdbc:postgresql://host:5432/rag_engine` |
| `DB_USERNAME` | Usuario de base de datos |
| `DB_PASSWORD` | Contraseña de base de datos |
| `KEYCLOAK_ISSUER_URI` | URI del realm de Keycloak |

Ejecutar con perfil de producción:

```bash
java -jar target/ai-secure-rag-engine.jar --spring.profiles.active=prod
```

---

## Autora

**Lanny Rivero**
Desarrolladora Backend — Java · Spring Boot · Spring AI · Sistemas Distribuidos
