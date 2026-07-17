package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;

/**
 * Input port for ingesting a document into the vector store.
 *
 * <p>
 * Accepts a document for asynchronous ingestion. The request is durably queued
 * and processed later by the background ingestion pipeline.
 */
public interface IngestDocumentUseCase {

    /**
     * Accepts a document ingestion request for asynchronous processing.
     *
     * @param command the ingest parameters including tenant, document ID, and raw
     *                content
     * @return the accepted ingestion request state for the document
     */
    IngestDocumentResult execute(IngestDocumentCommand command);
}
