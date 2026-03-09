-- V3: Add embedding_model column to document_chunks
--
-- Motivation:
-- If the embedding model changes (e.g. text-embedding-ada-002 →
-- text-embedding-3-large), existing vectors become incompatible.
-- Without this column there is no way to know which model generated
-- each chunk, making selective migration or controlled invalidation
-- impossible.
--
-- Retroactive DEFAULT: existing chunks are tagged with the model
-- currently in use. Switching models requires re-ingesting affected
-- documents and updating this value accordingly.

ALTER TABLE document_chunks
    ADD COLUMN embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-ada-002';