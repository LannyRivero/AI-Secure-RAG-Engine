# AI Secure RAG Engine

Backend RAG multi-tenant construido con Spring Boot, Spring AI, PostgreSQL + pgvector y Arquitectura Hexagonal.

Este repositorio no es un backend de chatbot de juguete. Es un diseño backend para equipos que necesitan respuestas de IA fundamentadas, aislamiento por tenant, controles operativos y una base de código capaz de soportar presión real de producción.

## Por qué importa este repo

La mayoría de los ejemplos de RAG resuelven sólo el camino feliz:

- un solo tenant
- una sola consulta vectorial
- una sola llamada al LLM
- sin modelo de seguridad
- sin realidad de despliegue
- sin postura operativa

Este proyecto está construido alrededor de los problemas que realmente vuelven difícil a un sistema RAG en producción:

- aislamiento multi-tenant estricto
- generación basada sólo en evidencia
- control de relevancia antes de llamar al modelo
- endurecimiento contra prompt injection
- autenticación enterprise con límites de rol claros
- auditoría, métricas, trazas y documentación operativa

El resultado es una referencia de backend AI flagship para preguntas y respuestas seguras y fundamentadas sobre conocimiento privado.

## Qué hace

A grandes rasgos, el servicio ingesta documentos privados, los fragmenta, genera embeddings, almacena los datos de recuperación en PostgreSQL + pgvector y responde preguntas usando únicamente contexto recuperado y relevante.

Si no encuentra evidencia suficiente, el sistema devuelve `no_evidence` en lugar de inventar una respuesta.

En la práctica, este backend está pensado para productos que necesitan:

- indexar una base de conocimiento privada
- responder preguntas en lenguaje natural usando sólo evidencia recuperada
- evitar fuga de datos entre organizaciones o tenants
- operar el sistema con seguridad, auditoría y observabilidad reales

Capacidades principales:

| Capacidad | Qué significa en la práctica |
|---|---|
| Respuestas fundamentadas | El LLM responde sólo con evidencia recuperada |
| Multi-tenancy | El aislamiento por tenant se aplica en validación de dominio, extracción desde JWT y consultas SQL |
| Recuperación híbrida | La búsqueda vectorial semántica puede combinarse con full-text search de PostgreSQL |
| Control de relevancia | Los resultados de baja calidad se filtran antes de llamar al LLM |
| Autenticación segura | Resource server JWT con Keycloak, roles y extracción del tenant claim |
| Hardening de prompt | La entrada del usuario se sanitiza antes de construir el prompt |
| Rate limiting | Cuotas por tenant con enforcement distribuido vía Redis en `dev` y `prod` |
| Auditabilidad | Las operaciones sensibles y los `401/403` emiten logs estructurados `SECURITY_AUDIT` |
| Observabilidad | Métricas, trazas, dashboards y alertas forman parte del repo |

---

Este servicio sigue Arquitectura Hexagonal con DDD.

Strict Hexagonal Architecture (Ports & Adapters) with DDD. The domain layer has zero framework dependencies.
```
HTTP Request
    -> Controller
    -> Use Case Port
    -> Application Service
    -> Output Ports
    -> Infrastructure Adapters
```

Responsabilidades por capa:

| Capa | Responsabilidad |
|---|---|
| `domain/` | Modelo de negocio puro y value objects sin dependencias de framework |
| `application/` | Casos de uso, orquestación, políticas y contratos de puertos de salida |
| `infrastructure/` | Adaptadores web, persistencia, integración con OpenAI, recuperación con pgvector y rate limiting con Redis |
| `security/` | JWT resource server, extracción de tenant, reglas de acceso y hooks de auditoría |
| `shared/` | Preocupaciones transversales como manejo de errores y observabilidad |

Esa separación es deliberada. Mantiene las reglas de negocio testeables, evita fugas de framework hacia el core y hace que el repo se lea como un sistema backend en serio, no como una pila de adapters sin criterio.

## Decisiones de arquitectura

Estas son las decisiones que vuelven coherente el diseño en lugar de accidental.

### 1. `TenantId` es un value object de primera clase

La identidad del tenant no viaja como un string cualquiera. Se valida al construirse y se propaga a través de los límites de aplicación e infraestructura.

Por qué importa:

- los tenant IDs inválidos fallan temprano
- el modelo de dominio codifica la regla de aislamiento
- los adapters SQL reciben inputs tenant-aware por contrato

### 2. La calidad de recuperación se verifica antes de generar

`RelevancePolicy` impone un umbral mínimo de similitud antes de llamar al LLM.

Por qué importa:

- reduce presión de alucinación
- evita que recuperación de bajo valor se convierta en falsa confianza
- hace que `no_evidence` sea un resultado de negocio válido, no un edge case

### 3. La construcción del prompt se trata como frontera de seguridad

`PromptBuilder` elimina caracteres de control, limita longitud y da forma explícita al prompt fundamentado.

Por qué importa:

- prompt injection se trata como preocupación operativa, no como detalle de demo
- la entrada del usuario se normaliza antes de tocar instrucciones orientadas al modelo

### 4. La autenticación se externaliza a Keycloak, no se improvisa en la app

El backend corre como OAuth2 JWT resource server y extrae la identidad del tenant desde claims del token.

Por qué importa:

- los límites de rol se mantienen explícitos
- el tenant scoping queda atado a identidad firmada
- el repo modela una topología de seguridad enterprise real

### 5. Las cuotas son por tenant y fallan cerrado

Los perfiles `dev` y `prod` usan rate limiting distribuido con Redis. Si el backend de cuotas no está disponible, la API responde `503` en vez de cambiar silenciosamente a buckets locales por instancia.

Por qué importa:

- las cuotas siguen siendo consistentes entre instancias
- escalar no multiplica límites por accidente
- las decisiones de resiliencia preservan corrección, no sólo apariencia de uptime

## Por qué Spring AI

Spring AI no está aquí porque esté de moda. Está porque encaja con la arquitectura y con la forma operativa del proyecto.

Por qué se usa en este repo:

- da una abstracción Java-first consistente para embeddings y chat models
- integra limpio con configuración y dependency management de Spring Boot
- reduce plumbing de proveedor para que el repo se enfoque en retrieval, policy y seguridad
- convive naturalmente con el setup actual de `pgvector` y el ecosistema Spring

El punto importante es este: Spring AI se usa como capa de integración, no como arquitectura. La arquitectura sigue estando dirigida por puertos, adapters, políticas y restricciones de dominio.

## Flujo de recuperación y respuesta

### Ingesta

1. Acepta contenido del documento y un `documentId`
2. Extrae el tenant desde los claims JWT
3. Elimina chunks existentes para el mismo tenant/documento
4. Fragmenta el contenido
5. Genera embeddings
6. Persiste chunks y vectores

### Query

1. Valida la request y aplica rate limit al tenant
2. Genera embedding de la query
3. Recupera chunks relevantes sólo para ese tenant
4. Aplica el umbral de relevancia
5. Construye un prompt sanitizado y fundamentado
6. Llama al LLM sólo si la evidencia es suficiente
7. Devuelve respuesta más evidencia, o `no_evidence`

Éste es el contrato real del servicio: retrieval no es un helper de generación; es la compuerta que decide si generar está permitido o no.

## Cómo se usa

El flujo operativo principal del backend es simple:

1. Ingestar documentos para un tenant
2. Consultar esos documentos con una pregunta en lenguaje natural
3. Eliminar documentos cuando ya no deban estar disponibles

Endpoints principales:

- `POST /rag/ingest` para indexar documentos
- `POST /rag/query` para consultar la base de conocimiento
- `DELETE /rag/documents/{documentId}` para eliminar un documento

Uso esperado por rol:

- `PLATFORM_ADMIN` ingesta, elimina y opera superficies administrativas
- `ORG_MEMBER` consulta contenido de su tenant
- todas las operaciones están aisladas por tenant y protegidas por JWT

Este repo está pensado para integrarse como backend de un portal interno, un copiloto enterprise, un buscador documental o una API privada de asistentes de conocimiento.

## Seguridad y postura operativa

Este repo trata intencionalmente la seguridad y el comportamiento runtime como preocupaciones de primera clase.

Puntos destacados:

- autenticación JWT respaldada por Keycloak
- separación de roles entre `PLATFORM_ADMIN` y `ORG_MEMBER`
- extracción del tenant desde el claim `attributes.tenant_id`
- modelo secure-by-default para Swagger y Prometheus
- eventos de auditoría estructurados para operaciones sensibles y fallos de auth
- logs, métricas y trazas correlacionadas para análisis de incidentes

Docs relacionados:

- `docs/security-operations.md`
- `docs/observability.md`
- `docs/deployment-production.md`

## Stack tecnológico

| Área | Elección |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5 |
| Integración AI | Spring AI 1.1.2 |
| Proveedor LLM | OpenAI |
| Retrieval store | PostgreSQL 16 + pgvector |
| Modo de búsqueda | Vector o híbrido |
| Auth | Keycloak 24 |
| Rate limiting | Bucket4j + Redis |
| Resiliencia | Resilience4j |
| Observabilidad | Micrometer, Actuator, OpenTelemetry |
| Testing | JUnit 5, Mockito, Testcontainers, MockMvc |

## Desarrollo local

### Prerrequisitos

- Java 21
- Docker
- OpenAI API key

### Levantar localmente

### 2. Start infrastructure
```bash
cp .env.example .env
docker compose up -d
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d
$env:OPENAI_API_KEY="sk-..."
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Chequeo mínimo de readiness:

1. Confirmar que `db`, `keycloak-db`, `keycloak` y `redis` estén `healthy`
2. Verificar que el realm responda:

```bash
curl http://localhost:8180/realms/rag-engine/.well-known/openid-configuration
```

3. Recién después arrancar la aplicación Spring Boot

Endpoints locales:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Keycloak realm: `http://localhost:8180/realms/rag-engine`

Usuarios demo importados localmente desde `keycloak/realm-export.json`:

| Username | Password | Role |
|---|---|---|
| `admin-test` | `password` | `PLATFORM_ADMIN` |
| `tecnica-test` | `password` | `ORG_MEMBER` |

## Historia de despliegue

Este repositorio ahora cuenta la historia de despliegue de forma honesta.

- `dev` es local-first y optimizado para workflow de desarrollo
- `prod` es el modelo operativo objetivo real
- el staging público free-tier sólo es válido donde la plataforma realmente encaja con el grafo de dependencias

Realidad importante:

- el servicio de aplicación puede empaquetarse y describirse para staging tipo Render
- el stack completo sigue dependiendo de PostgreSQL, Redis, OpenAI y un issuer real de Keycloak
- el hosting cero costo no alcanza para correr toda la arquitectura pretendida de punta a punta sin compromisos

Eso no es una debilidad del repo. Es un reflejo honesto de restricciones reales de un backend.

Resumen ejecutivo de despliegue real:

- artefacto principal: `target/ai-secure-rag-engine.jar`
- perfil objetivo: `prod`
- contrato mínimo: `OPENAI_API_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `KEYCLOAK_ISSUER_URI`
- dependencias externas reales: PostgreSQL + pgvector, Redis y Keycloak

Para el contrato completo de despliegue y la guía runtime, ver:

- `docs/deployment-production.md`

## Límites conocidos

Ningún backend está completo si no deja sus límites escritos.

Límites actuales de este repo:

- el camino por defecto de proveedor está centrado en OpenAI
- un staging público full-stack con la arquitectura basada en Keycloak no es viable en los free tiers más pequeños
- todavía no hay manifests de infraestructura para Kubernetes o runtimes cloud específicos
- el repo está optimizado como referencia de backend y servicio platform-ready, no como producto completo con UI

## Roadmap

Próximos pasos de alto valor:

- agregar una ruta de staging paga o production-like para el stack completo
- agregar manifests de infraestructura para un target concreto como Kubernetes o servicios gestionados vía Terraform
- ampliar flexibilidad de proveedor sin romper los contratos actuales de policy y retrieval
- profundizar readiness operativa con automatización de despliegue y promotion workflows entre entornos
- extender la evaluación de evidencia y retrieval más allá de tests funcionales hacia checks tipo benchmark

## Por qué este repo ya se ve como flagship backend AI

Porque demuestra la parte difícil de la ingeniería de backend AI:

- no sólo llamar a un modelo
- no sólo guardar vectores
- no sólo exponer endpoints

Muestra cómo diseñar un servicio de IA que respete límites arquitectónicos, aislamiento multi-tenant, postura de seguridad, visibilidad operativa y realidad de despliegue al mismo tiempo.

También demuestra evidencia técnica concreta:

- separación arquitectónica consistente
- testing en dominio, aplicación, infraestructura y web
- documentación operativa de seguridad, observabilidad y despliegue
- decisiones explícitas sobre límites reales de staging y producción

## Licencia

Este proyecto se distribuye bajo licencia `MIT`.

Ver el archivo `LICENSE` para el texto completo.

## Autoría

Proyecto desarrollado por **Lanny Rivero**.

Perfil del proyecto:

- Backend Engineering
- Java y Spring Boot
- Sistemas distribuidos
- Arquitectura aplicada para productos AI backend
