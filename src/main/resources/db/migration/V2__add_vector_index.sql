-- V2: Índice HNSW para retrieval semántico eficiente
--
-- Sin este índice, cada query al RAG hace un sequential scan completo
-- de la tabla document_chunks comparando el vector de query contra
-- todos los embeddings almacenados.
--
-- HNSW (Hierarchical Navigable Small World) es el algoritmo recomendado
-- por pgvector para búsqueda aproximada de vecinos más cercanos (ANN).
-- Ofrece mejor rendimiento en query que IVFFlat sin requerir entrenamiento previo.
--
-- Parámetros elegidos:
--   m = 16             : número de conexiones por nodo. Rango recomendado: 8-64.
--                        16 es el valor por defecto, buen balance precisión/memoria.
--   ef_construction = 64: tamaño del grafo durante construcción. Mayor = más preciso,
--                         más lento al indexar. 64 es el valor por defecto de pgvector.
--
-- vector_cosine_ops: operador coseno, consistente con la distancia usada en el retrieval
--   (embedding <=> ?::vector) que calcula distancia coseno.
--   Debe coincidir con el operador usado en las queries o el índice no se usa.
--
-- ADR: se elige HNSW sobre IVFFlat porque:
--   - IVFFlat requiere VACUUM + número de filas para calcular listas (no disponible en Flyway)
--   - HNSW funciona correctamente con tablas vacías desde el primer momento
--   - HNSW tiene mejor latencia en query para volúmenes de hasta varios millones de vectores

CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
