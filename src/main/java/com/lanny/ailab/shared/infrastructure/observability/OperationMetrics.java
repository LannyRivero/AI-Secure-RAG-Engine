package com.lanny.ailab.shared.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Low-cardinality operation metrics used for enterprise diagnostics.
 */
@Component
public class OperationMetrics {

        private final MeterRegistry registry;

        public OperationMetrics(MeterRegistry registry) {
                this.registry = registry;
        }

        public void recordOperation(String operation, String outcome, Duration duration) {
                Counter.builder("rag.operation.requests")
                                .description("Business operation executions by operation and outcome")
                                .tag("operation", operation)
                                .tag("outcome", outcome)
                                .register(registry)
                                .increment();

                Timer.builder("rag.operation.latency")
                                .description("Business operation latency by operation and outcome")
                                .publishPercentiles(0.5, 0.95, 0.99)
                                .tag("operation", operation)
                                .tag("outcome", outcome)
                                .register(registry)
                                .record(duration);
        }

        public void recordProviderCall(String provider, String operation, String outcome, Duration duration) {
                Counter.builder("rag.provider.calls")
                                .description("Provider calls by provider, operation, and outcome")
                                .tag("provider", provider)
                                .tag("operation", operation)
                                .tag("outcome", outcome)
                                .register(registry)
                                .increment();

                Timer.builder("rag.provider.latency")
                                .description("Provider call latency by provider, operation, and outcome")
                                .publishPercentiles(0.5, 0.95, 0.99)
                                .tag("provider", provider)
                                .tag("operation", operation)
                                .tag("outcome", outcome)
                                .register(registry)
                                .record(duration);
        }

        public void recordRetrieval(String retriever, String phase, String outcome, int resultCount,
                        Duration duration) {
                Counter.builder("rag.retrieval.requests")
                                .description("Retrieval executions by retriever, phase, and outcome")
                                .tag("retriever", retriever)
                                .tag("phase", phase)
                                .tag("outcome", outcome)
                                .register(registry)
                                .increment();

                Timer.builder("rag.retrieval.latency")
                                .description("Retrieval latency by retriever, phase, and outcome")
                                .publishPercentiles(0.5, 0.95, 0.99)
                                .tag("retriever", retriever)
                                .tag("phase", phase)
                                .tag("outcome", outcome)
                                .register(registry)
                                .record(duration);

                DistributionSummary.builder("rag.retrieval.results")
                                .description("Retrieved chunk counts by retriever, phase, and outcome")
                                .baseUnit("chunks")
                                .tag("retriever", retriever)
                                .tag("phase", phase)
                                .tag("outcome", outcome)
                                .register(registry)
                                .record(resultCount);
        }
}
