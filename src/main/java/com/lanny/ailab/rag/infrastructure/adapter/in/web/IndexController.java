package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.service.DocumentIndexService;
import com.lanny.ailab.security.application.TenantContext;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("!prod")
@RestController
@RequestMapping("/dev")
public class IndexController {

    private final DocumentIndexService indexService;
    private final TenantContext tenantContext;

    public IndexController(DocumentIndexService indexService,
            TenantContext tenantContext) {
        this.indexService = indexService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/index")
    public ResponseEntity<Void> index(
            @RequestParam String documentId,
            @RequestBody String content) {

        String tenantId = tenantContext.getCurrentTenantId();
        indexService.index(tenantId, documentId, content);

        return ResponseEntity.noContent().build();
    }
}
