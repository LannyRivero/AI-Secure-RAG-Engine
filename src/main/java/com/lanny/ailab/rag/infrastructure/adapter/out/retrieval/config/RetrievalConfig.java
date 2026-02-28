package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval.config;

import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.infrastructure.adapter.out.retrieval.InMemoryRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetrievalConfig {

    @Bean
    public RetrievalPort retrievalPort() {
        return new InMemoryRetriever();
    }
}
