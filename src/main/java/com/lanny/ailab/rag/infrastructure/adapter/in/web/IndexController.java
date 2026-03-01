package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.service.DocumentIndexService;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("!prod")
@RestController
@RequestMapping("/dev")
public class IndexController {

    private final DocumentIndexService indexService;

    public IndexController(DocumentIndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping("/index")
    public void index(@RequestParam String tenantId,
            @RequestParam String documentId,
            @RequestBody String content) {

        indexService.index(tenantId, documentId, content);
    }
}
