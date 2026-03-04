package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;
import com.lanny.ailab.security.application.TenantContext;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final QueryRagUseCase queryRagUseCase;
    private final QueryRagWebMapper mapper;
    private final TenantContext tenantContext;

    public RagController(QueryRagUseCase queryRagUseCase,
            QueryRagWebMapper mapper,
            TenantContext tenantContext) {
        this.queryRagUseCase = queryRagUseCase;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryRagResponse> query(
            @Valid @RequestBody QueryRagRequest request) {

        String tenantId = tenantContext.getCurrentTenantId();
        var command = mapper.toCommand(request, tenantId);
        var result = queryRagUseCase.execute(command);

        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
