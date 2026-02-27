package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.result.QueryRagResult;

public interface QueryRagUseCase {

    QueryRagResult execute(QueryRagCommand command);
}
