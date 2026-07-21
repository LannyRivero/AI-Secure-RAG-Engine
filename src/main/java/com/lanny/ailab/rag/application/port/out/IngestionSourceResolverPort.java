package com.lanny.ailab.rag.application.port.out;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.model.ResolvedIngestionSource;

/**
 * Outbound port that resolves connector-specific source inputs into normalized
 * text before the request is durably enqueued.
 */
public interface IngestionSourceResolverPort {

    /**
     * Resolves a source request into normalized text plus source metadata.
     *
     * @param command ingestion request accepted by the use case
     * @return normalized content plus source metadata
     */
    ResolvedIngestionSource resolve(IngestDocumentCommand command);
}
