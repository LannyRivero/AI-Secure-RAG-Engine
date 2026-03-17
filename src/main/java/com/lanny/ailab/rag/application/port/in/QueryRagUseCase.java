package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.result.QueryRagResult;

/**
 * Input port for executing a RAG query.
 *
 * <p>Orchestrates retrieval of relevant document chunks, relevance filtering,
 * prompt construction, and LLM response generation.
 */
public interface QueryRagUseCase {

    /**
     * Executes a RAG query and returns the generated answer with supporting evidence.
     *
     * @param command the query parameters including tenant, user query, and retrieval options
     * @return the LLM answer and the chunks used as evidence, or a no-evidence result
     */
    QueryRagResult execute(QueryRagCommand command);
}
