package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class RetrieverConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rag.retriever", havingValue = "vector", matchIfMissing = true)
    public RetrievalPort vectorRetriever(EmbeddingPort embeddingPort,
            JdbcTemplate jdbcTemplate,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        return new PgVectorRetriever(embeddingPort, jdbcTemplate, operationMetrics, observationRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.retriever", havingValue = "hybrid")
    public RetrievalPort hybridRetriever(EmbeddingPort embeddingPort,
            JdbcTemplate jdbcTemplate,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        return new HybridRetriever(embeddingPort, jdbcTemplate, operationMetrics, observationRegistry);
    }
}
