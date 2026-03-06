package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;

public interface IngestDocumentUseCase {

    IngestDocumentResult execute(IngestDocumentCommand command);
}
