package com.lanny.ailab.rag.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics for the asynchronous ingestion pipeline.
 */
@Component
public class IngestionMetrics {

        private final Counter accepted;
        private final Counter completed;
        private final Counter retriesScheduled;
        private final Counter failed;
        private final Counter deadLettered;
        private final Timer processingLatency;
        private final AtomicInteger queuedGauge = new AtomicInteger();
        private final AtomicInteger processingGauge = new AtomicInteger();
        private final AtomicInteger failedGauge = new AtomicInteger();
        private final AtomicInteger deadLetterGauge = new AtomicInteger();

        public IngestionMetrics(MeterRegistry registry) {
                this.accepted = Counter.builder("rag.ingestion.accepted")
                                .description("Accepted ingestion requests")
                                .register(registry);
                this.completed = Counter.builder("rag.ingestion.completed")
                                .description("Completed ingestion jobs")
                                .register(registry);
                this.retriesScheduled = Counter.builder("rag.ingestion.retries_scheduled")
                                .description("Failed ingestion attempts rescheduled for retry")
                                .register(registry);
                this.failed = Counter.builder("rag.ingestion.failed")
                                .description("Failed ingestion attempts")
                                .register(registry);
                this.deadLettered = Counter.builder("rag.ingestion.dead_lettered")
                                .description("Jobs moved to dead-letter state after exhausting retries")
                                .register(registry);
                this.processingLatency = Timer.builder("rag.ingestion.processing.latency")
                                .description("End-to-end async ingestion processing latency")
                                .publishPercentiles(0.5, 0.95, 0.99)
                                .publishPercentileHistogram()
                                .register(registry);

                Gauge.builder("rag.ingestion.queued", queuedGauge, AtomicInteger::get)
                                .description("Current number of queued ingestion jobs")
                                .register(registry);
                Gauge.builder("rag.ingestion.processing", processingGauge, AtomicInteger::get)
                                .description("Current number of processing ingestion jobs")
                                .register(registry);
                Gauge.builder("rag.ingestion.failed.queue", failedGauge, AtomicInteger::get)
                                .description("Current number of failed ingestion jobs awaiting manual action")
                                .register(registry);
                Gauge.builder("rag.ingestion.dead_letter.queue", deadLetterGauge, AtomicInteger::get)
                                .description("Current number of dead-lettered ingestion jobs")
                                .register(registry);
        }

        public void incrementAccepted() {
                accepted.increment();
        }

        public void incrementCompleted() {
                completed.increment();
        }

        public void incrementRetriesScheduled() {
                retriesScheduled.increment();
        }

        public void incrementFailed() {
                failed.increment();
        }

        public void incrementDeadLettered() {
                deadLettered.increment();
        }

        public Timer processingLatency() {
                return processingLatency;
        }

        public void updateQueueDepths(long queued, long processing, long failedQueue, long deadLetterQueue) {
                queuedGauge.set((int) queued);
                processingGauge.set((int) processing);
                failedGauge.set((int) failedQueue);
                deadLetterGauge.set((int) deadLetterQueue);
        }

        public double accepted() {
                return accepted.count();
        }

        public double completed() {
                return completed.count();
        }

        public double retriesScheduled() {
                return retriesScheduled.count();
        }

        public double failed() {
                return failed.count();
        }

        public double deadLettered() {
                return deadLettered.count();
        }

        public double queued() {
                return queuedGauge.get();
        }

        public double processing() {
                return processingGauge.get();
        }

        public double failedQueue() {
                return failedGauge.get();
        }

        public double deadLetterQueue() {
                return deadLetterGauge.get();
        }
}
