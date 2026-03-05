package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.result.DeleteDocumentResult;

public interface DeleteDocumentUseCase {

    /**
     * Deletes all chunks associated with the given documentId within a tenant.
     * Idempotent: if the document does not exist, returns notFound without error.
     *
     * @param command delete input
     * @return result indicating whether the document existed and was deleted
     */
    DeleteDocumentResult execute(DeleteDocumentCommand command);
}
