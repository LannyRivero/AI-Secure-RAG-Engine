package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.model.RetrievalFilter;
import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.policy.RelevancePolicy;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.SimilarityScore;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class QueryRagServiceTest {

        @Mock
        private LlmChatPort llmChatPort;

        @Mock
        private RetrievalPort retrievalPort;

        @Mock
        private PromptBuilder promptBuilder;

        @Mock
        private RelevancePolicy relevancePolicy;

        @Mock
        private OperationMetrics operationMetrics;

        private RagMetrics ragMetrics;
        private QueryRagService service;

        @BeforeEach
        void setUp() {
                ragMetrics = new RagMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
                service = new QueryRagService(
                                llmChatPort,
                                retrievalPort,
                                promptBuilder,
                                relevancePolicy,
                                ragMetrics,
                                operationMetrics,
                                ObservationRegistry.NOOP,
                                3,
                                20,
                                "hybrid");
        }

        @Test
        @DisplayName("When retrieval finds no chunks, the service returns no evidence and does not call LLM")
        void returns_no_evidence_when_retrieval_finds_no_chunks() {
                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(List.of());

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
                verify(llmChatPort, never()).generateAnswer(anyString());
        }

        @Test
        @DisplayName("When relevance policy rejects chunks, the service returns no evidence and does not call LLM")
        void returns_no_evidence_when_relevance_policy_rejects_chunks() {
                var chunks = List.of(chunk("doc-1", 0.3));

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(false);

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
                verify(llmChatPort, never()).generateAnswer(anyString());
        }

        @Test
        @DisplayName("When LLM returns a no-evidence token, the service returns no evidence")
        void returns_no_evidence_when_llm_returns_no_evidence_token() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(true);
                when(promptBuilder.build(anyString(), any()))
                                .thenReturn("built prompt");
                when(llmChatPort.generateAnswer(anyString()))
                                .thenReturn("no_evidence");

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
        }

        @Test
        @DisplayName("When LLM returns a blank response, the service returns no evidence")
        void returns_no_evidence_when_llm_returns_blank_response() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(true);
                when(promptBuilder.build(anyString(), any()))
                                .thenReturn("built prompt");
                when(llmChatPort.generateAnswer(anyString()))
                                .thenReturn("   ");

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
        }

        @Test
        @DisplayName("When all conditions are met, the service returns an answer with evidence")
        void returns_answer_with_evidence_when_all_conditions_met() {
                var chunks = List.of(chunk("doc-1", 0.9));
                String expectedAnswer = "UNADA is a social resources platform.";

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(true);
                when(promptBuilder.build(anyString(), any()))
                                .thenReturn("built prompt");
                when(llmChatPort.generateAnswer(anyString()))
                                .thenReturn(expectedAnswer);

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isTrue();
                assertThat(result.answer()).isEqualTo(expectedAnswer);
                assertThat(result.evidence()).containsExactly(chunks.get(0));
        }

        @Test
        @DisplayName("When filters are present, the service forwards them to retrieval")
        void forwards_filters_to_retrieval() {
                RetrievalFilter filters = new RetrievalFilter("policy", null, List.of("security"), null, null, null,
                                null);
                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(List.of());

                service.execute(new QueryRagCommand("What is UNADA?", TenantId.from("org-test"), null, 3, filters));

                verify(retrievalPort).retrieve(anyString(), any(TenantId.class), eq(3), eq(filters));
        }

        @Test
        @DisplayName("When a query is successful, the service increments metrics correctly")
        void increments_metrics_correctly_on_successful_query() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(true);
                when(promptBuilder.build(anyString(), any()))
                                .thenReturn("built prompt");
                when(llmChatPort.generateAnswer(anyString()))
                                .thenReturn("valid answer");

                service.execute(command("What is UNADA?"));

                assertThat(ragMetrics.total()).isEqualTo(1);
                assertThat(ragMetrics.llmCalls()).isEqualTo(1);
                assertThat(ragMetrics.noEvidence()).isEqualTo(0);
        }

        @Test
        @DisplayName("When retrieval returns no chunks, the service increments no-evidence metric")
        void increments_no_evidence_metric_when_retrieval_empty() {
                when(retrievalPort.retrieve(anyString(), any(TenantId.class), anyInt(), any(RetrievalFilter.class)))
                                .thenReturn(List.of());

                service.execute(command("What is UNADA?"));

                assertThat(ragMetrics.total()).isEqualTo(1);
                assertThat(ragMetrics.noEvidence()).isEqualTo(1);
                assertThat(ragMetrics.llmCalls()).isEqualTo(0);
        }

        // --- topK resolution tests ---

        @Test
        @DisplayName("When command has null top_k, the service uses the default top_k")
        void uses_default_top_k_when_command_has_null_top_k() {
                // defaultTopK=3, maxTopK=20 in service constructor below
                var serviceWithDefaults = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 3, 20,
                                "hybrid");

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), eq(3), any(RetrievalFilter.class)))
                                .thenReturn(List.of());

                serviceWithDefaults.execute(
                                new QueryRagCommand("query", TenantId.from("org-test"), null, null,
                                                RetrievalFilter.empty()));

                verify(retrievalPort).retrieve(anyString(), any(TenantId.class), eq(3), any(RetrievalFilter.class));
        }

        @Test
        @DisplayName("When command's top_k exceeds max, the service clamps it to max")
        void clamps_top_k_to_max_when_caller_exceeds_limit() {
                var serviceWithDefaults = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 3, 20,
                                "hybrid");

                when(retrievalPort.retrieve(anyString(), any(TenantId.class), eq(20), any(RetrievalFilter.class)))
                                .thenReturn(List.of());

                serviceWithDefaults.execute(
                                new QueryRagCommand("query", TenantId.from("org-test"), null, 999,
                                                RetrievalFilter.empty()));

                verify(retrievalPort).retrieve(anyString(), any(TenantId.class), eq(20), any(RetrievalFilter.class));
        }

        @Test
        @DisplayName("resolveTopK returns default when input is null")
        void resolve_top_k_returns_default_when_null() {
                var s = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 5, 20,
                                "hybrid");

                assertThat(s.resolveTopK(null)).isEqualTo(5);
        }

        @Test
        @DisplayName("resolveTopK clamps to max when input exceeds max")
        void resolve_top_k_clamps_to_max() {
                var s = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 3, 10,
                                "hybrid");

                assertThat(s.resolveTopK(50)).isEqualTo(10);
        }

        @Test
        @DisplayName("resolveTopK clamps to min of one when input is less than one")
        void resolve_top_k_clamps_to_min_one() {
                var s = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 3, 10,
                                "hybrid");

                assertThat(s.resolveTopK(0)).isEqualTo(1);
        }

        @Test
        @DisplayName("resolveTopK returns the input value when it is within the allowed range")
        void resolve_top_k_returns_value_when_within_range() {
                var s = new QueryRagService(
                                llmChatPort, retrievalPort, promptBuilder,
                                relevancePolicy, ragMetrics, operationMetrics, ObservationRegistry.NOOP, 3, 10,
                                "hybrid");

                assertThat(s.resolveTopK(7)).isEqualTo(7);
        }

        // helpers

        private QueryRagCommand command(String query) {
                return new QueryRagCommand(query, TenantId.from("org-test"), null, 3, RetrievalFilter.empty());
        }

        private DocumentChunk chunk(String documentId, double score) {
                return new DocumentChunk(documentId, TenantId.from("org-test"), "test content",
                                SimilarityScore.of(score), DocumentMetadata.empty());
        }
}
