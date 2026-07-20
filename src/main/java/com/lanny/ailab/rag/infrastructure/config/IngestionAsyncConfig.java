package com.lanny.ailab.rag.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the background executor used by the asynchronous ingestion worker.
 */
@Configuration
public class IngestionAsyncConfig {

    /**
     * Creates the executor that processes claimed ingestion jobs concurrently.
     *
     * @param poolSize      configured worker concurrency
     * @param queueCapacity buffered work capacity before callers execute inline
     * @return initialized ingestion executor
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor ingestionTaskExecutor(
            @Value("${app.rag.ingestion.worker-concurrency:2}") int poolSize,
            @Value("${app.rag.ingestion.worker-queue-capacity:100}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ingestion-worker-");
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
