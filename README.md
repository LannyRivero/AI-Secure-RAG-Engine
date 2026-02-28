# 🧠 AI Secure RAG Engine

> Enterprise-grade Retrieval-Augmented Generation (RAG) engine built with Spring Boot, Hexagonal Architecture and Domain-Driven Design.

---

## 📌 Overview

AI Secure RAG Engine is a modular and extensible backend system designed to:

- Ingest domain documents
- Generate embeddings
- Store vectors using PostgreSQL + pgvector
- Retrieve relevant context
- Generate secure AI responses using LLM integration

The system is designed following:

- ✅ Hexagonal Architecture (Ports & Adapters)
- ✅ Domain-Driven Design (DDD)
- ✅ Clean separation of concerns
- ✅ Environment-based configuration
- ✅ Profile-driven infrastructure
- ✅ Enterprise-ready structure

---

## 🏗 Architecture

This project follows a strict Hexagonal (Ports & Adapters) model combined with DDD.

Controller → UseCase → Domain → Ports → Adapters

### Layered Structure

```
rag
├── domain
│ ├── model
│ ├── valueobject
│ └── service
│
├── application
│ ├── command
│ ├── result
│ ├── port
│ │ ├── in
│ │ └── out
│ └── service
│
├── adapter
│ ├── in
│ │ └── web
│ └── out
│ ├── openai
│ ├── pgvector
│ └── persistence
│
└── infrastructure
└── config
```

### Architectural Principles

- The **domain layer does not depend on Spring**
- Use cases depend only on domain and ports
- Infrastructure implements outbound ports
- Controllers depend only on use cases
- Business rules are isolated from frameworks

---

## 🔐 Security & Isolation

The system supports:

- Multi-tenant context isolation
- Tenant-based retrieval filtering
- Controlled prompt construction
- Extensible security module (future RBAC integration)

---

## ⚙️ Technology Stack

| Layer | Technology |
|-------|------------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5 |
| ORM | Hibernate / JPA |
| Database | PostgreSQL 15 |
| Vector Store | pgvector |
| AI Integration | Spring AI / OpenAI |
| Build Tool | Maven |
| Containerization | Docker |

---

## 🗄 Database

PostgreSQL runs in Docker with pgvector enabled.

Vector storage is handled via:

- `VectorStorePort`
- `PgVectorStoreAdapter`

---

## 🌍 Environment Profiles

The project supports multiple runtime profiles:

- `dev`
- `test`
- `prod`

Configuration files:

application.yml
application-dev.yml
application-test.yml
application-prod.yml

Run with profile:
```
-Dspring.profiles.active=dev
```

---

## 🚀 Running the Project

### 1️⃣ Start PostgreSQL
```
docker compose up -d
```


Ensure the configured port (e.g., 5433) is available.

---

### 2️⃣ Run Application

From IDE or:
```
mvn spring-boot:run
```

Or packaged:

```
java -jar target/ai-lab.jar
```


---

## 🧩 Core Use Cases

### QueryRagUseCase

Handles AI queries by:

1. Validating input
2. Retrieving relevant document chunks
3. Building contextual prompt
4. Calling LLM
5. Returning structured response

---

### IngestDocumentUseCase

Responsible for:

1. Receiving raw document
2. Chunking content
3. Generating embeddings
4. Persisting vectors

---

## 📦 Commands & Results Pattern

This project uses explicit Application Commands and Results:

- `QueryRagCommand`
- `QueryRagResult`
- `IngestDocumentCommand`
- `IngestDocumentResult`

Benefits:

- Clean input/output modeling
- Explicit use case contracts
- Full testability without HTTP layer
- Clear separation between transport and business logic

---

## 🧪 Testing Strategy

- Unit tests for domain logic
- Use case tests with mocked ports
- Integration tests for adapters
- Profile-based test configuration

---

## 📈 Observability (Planned)

- Structured logging
- Token usage tracking
- Retrieval traceability
- Prompt debugging

---

## 🎯 Project Goals

This repository is designed as:

- A production-grade RAG reference architecture
- A learning lab for Spring AI + pgvector
- A demonstration of enterprise backend design
- A scalable AI service foundation

---

## 👤 Author

Lanny Rivero  
Backend Developer | Java & Spring Boot | AI Systems  
