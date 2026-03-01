CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(50) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536)
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_tenant_id
    ON document_chunks (tenant_id);
```

**Por qué 1536:** es la dimensión del modelo `text-embedding-ada-002` de OpenAI. Si usas otro modelo con diferente dimensión, cambia este valor. Para los integration tests usaremos embeddings sintéticos de esa dimensión.

---**Nota:** Asegúrate de que el vector store en tu aplicación esté configurado para usar la misma dimensión que la tabla. Si usas un modelo diferente para generar embeddings, actualiza la dimensión en la tabla y en tu código.