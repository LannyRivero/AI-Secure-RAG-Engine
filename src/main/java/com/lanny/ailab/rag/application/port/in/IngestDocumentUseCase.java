package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;

/**
 * Input port for ingesting a document into the vector store.
 *
 * <p>Splits the document into chunks, generates embeddings, and stores them.
 * Implements upsert semantics: existing chunks for the same document are deleted
 * before the new ones are indexed.
 */
public interface IngestDocumentUseCase {

    /**
     * Ingests a document, replacing any previously indexed version.
     *
     * @param command the ingest parameters including tenant, document ID, and raw content
     * @return the result containing the document ID and number of chunks indexed
     */
    IngestDocumentResult execute(IngestDocumentCommand command);
}
