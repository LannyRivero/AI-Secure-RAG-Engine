package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.policy.RelevancePolicy;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

@Service
public class QueryRagService implements QueryRagUseCase {

    private static final String NO_EVIDENCE_TOKEN = "no_evidence";

    private static final Logger log = LoggerFactory.getLogger(QueryRagService.class);

    private final LlmChatPort llmChatPort;
    private final RetrievalPort retrievalPort;
    private final PromptBuilder promptBuilder;
    private final RelevancePolicy relevancePolicy;
    private final RagMetrics ragMetrics;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;
    private final int defaultTopK;
    private final int maxTopK;
    private final String retrieverType;

    public QueryRagService(
            LlmChatPort llmChatPort,
            RetrievalPort retrievalPort,
            PromptBuilder promptBuilder,
            RelevancePolicy relevancePolicy,
            RagMetrics ragMetrics,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry,
            @Value("${app.rag.default-top-k:3}") int defaultTopK,
            @Value("${app.rag.max-top-k:20}") int maxTopK,
            @Value("${app.rag.retriever:hybrid}") String retrieverType) {

        this.llmChatPort = llmChatPort;
        this.retrievalPort = retrievalPort;
        this.promptBuilder = promptBuilder;
        this.relevancePolicy = relevancePolicy;
        this.ragMetrics = ragMetrics;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
        this.defaultTopK = defaultTopK;
        this.maxTopK = maxTopK;
        this.retrieverType = retrieverType;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {
        int topK = resolveTopK(command.topK());
        String tenantId = command.tenantId().value();

        Instant started = Instant.now();
        String outcome = "success";
        String queryHash = fingerprint(command.query());
        Observation observation = Observation.start("rag.query.execute", observationRegistry)
                .lowCardinalityKeyValue("operation", "query")
                .lowCardinalityKeyValue("retriever", retrieverType)
                .highCardinalityKeyValue("tenant.id", tenantId)
                .highCardinalityKeyValue("query.id", String.valueOf(MDC.get("queryId")))
                .highCardinalityKeyValue("query.hash", queryHash);

        ragMetrics.incrementTotal();

        try (Observation.Scope scope = observation.openScope()) {
            MDC.put("operation", "query");
            MDC.put("queryHash", queryHash);

            var chunks = ragMetrics.retrievalLatency()
                    .record(() -> retrievalPort.retrieve(command.query(), command.tenantId(), topK, command.filters()));

            if (chunks.isEmpty()) {
                outcome = "no_evidence_empty_retrieval";
                ragMetrics.incrementNoEvidence();
                log.info(
                        "RAG_QUERY_COMPLETE tenantId={} topK={} chunksRetrieved=0 hasEvidence=false reason=empty_retrieval",
                        tenantId, topK);
                return QueryRagResult.noEvidence();
            }

            if (!relevancePolicy.isRelevant(chunks)) {
                outcome = "no_evidence_below_threshold";
                ragMetrics.incrementThresholdRejected();
                ragMetrics.incrementNoEvidence();
                log.info(
                        "RAG_QUERY_COMPLETE tenantId={} topK={} chunksRetrieved={} hasEvidence=false reason=below_threshold",
                        tenantId, topK, chunks.size());
                return QueryRagResult.noEvidence();
            }

            String prompt = promptBuilder.build(command.query(), chunks);
            ragMetrics.incrementLlmCalls();

            String answer = ragMetrics.llmLatency().record(() -> llmChatPort.generateAnswer(prompt));

            if (answer == null || answer.isBlank()) {
                outcome = "no_evidence_llm_blank";
                ragMetrics.incrementNoEvidence();
                log.info("RAG_QUERY_COMPLETE tenantId={} topK={} chunksRetrieved={} hasEvidence=false reason=llm_blank",
                        tenantId, topK, chunks.size());
                return QueryRagResult.noEvidence();
            }

            String normalized = answer.trim().toLowerCase();

            if (normalized.equals(NO_EVIDENCE_TOKEN)) {
                outcome = "no_evidence_llm_token";
                ragMetrics.incrementNoEvidence();
                log.info(
                        "RAG_QUERY_COMPLETE tenantId={} topK={} chunksRetrieved={} hasEvidence=false reason=llm_no_evidence",
                        tenantId, topK, chunks.size());
                return QueryRagResult.noEvidence();
            }

            log.info("RAG_QUERY_COMPLETE tenantId={} topK={} chunksRetrieved={} hasEvidence=true",
                    tenantId, topK, chunks.size());

            return QueryRagResult.withEvidence(answer, chunks);
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
            operationMetrics.recordOperation("query", outcome, Duration.between(started, Instant.now()));
            MDC.remove("operation");
            MDC.remove("queryHash");
        }
    }

    /**
     * Resolves the effective topK value.
     * - If caller provides a value: clamp to [1, maxTopK]
     * - If caller provides null: use defaultTopK
     *
     * The DTO already validates topK <= 20 via @Max.
     * The clamp here is a second line of defence for non-HTTP callers.
     */
    int resolveTopK(Integer requested) {
        if (requested == null)
            return defaultTopK;
        return Math.max(1, Math.min(requested, maxTopK));
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
