package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final QueryRagUseCase queryRagUseCase;
    private final QueryRagWebMapper mapper;

    public RagController(QueryRagUseCase queryRagUseCase,
            QueryRagWebMapper mapper) {
        this.queryRagUseCase = queryRagUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryRagResponse> query(
            @Valid @RequestBody QueryRagRequest request) {

        var command = mapper.toCommand(request);
        var result = queryRagUseCase.execute(command);

        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
