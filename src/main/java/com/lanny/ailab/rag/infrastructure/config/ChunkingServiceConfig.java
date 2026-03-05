package com.lanny.ailab.rag.infrastructure.config;

import com.lanny.ailab.rag.domain.service.ChunkingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChunkingService lives in the domain (no Spring dependency).
 * This config bridges it into the Spring context.
 *
 * chunk size and overlap are fixed at MVP values.
 * Externalise to application.yaml when tuning becomes necessary.
 */
@Configuration
public class ChunkingServiceConfig {

    @Bean
    public ChunkingService chunkingService() {
        return new ChunkingService(512, 50);
    }
}