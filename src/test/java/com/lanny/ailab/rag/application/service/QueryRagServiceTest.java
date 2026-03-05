package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.policy.RelevancePolicy;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRagServiceTest {

        @Mock
        private LlmChatPort llmChatPort;

        @Mock
        private RetrievalPort retrievalPort;

        @Mock
        private PromptBuilder promptBuilder;

        @Mock
        private RelevancePolicy relevancePolicy;

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
                                ragMetrics);
        }

        @Test
        void returns_no_evidence_when_retrieval_finds_no_chunks() {
                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
                                .thenReturn(List.of());

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
                verify(llmChatPort, never()).generateAnswer(anyString());
        }

        @Test
        void returns_no_evidence_when_relevance_policy_rejects_chunks() {
                var chunks = List.of(chunk("doc-1", 0.3));

                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
                                .thenReturn(chunks);
                when(relevancePolicy.isRelevant(chunks))
                                .thenReturn(false);

                var result = service.execute(command("What is UNADA?"));

                assertThat(result.hasEvidence()).isFalse();
                verify(llmChatPort, never()).generateAnswer(anyString());
        }

        @Test
        void returns_no_evidence_when_llm_returns_no_evidence_token() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
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
        void returns_no_evidence_when_llm_returns_blank_response() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
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
        void returns_answer_with_evidence_when_all_conditions_met() {
                var chunks = List.of(chunk("doc-1", 0.9));
                String expectedAnswer = "UNADA is a social resources platform.";

                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
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
        void increments_metrics_correctly_on_successful_query() {
                var chunks = List.of(chunk("doc-1", 0.9));

                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
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
        void increments_no_evidence_metric_when_retrieval_empty() {
                when(retrievalPort.retrieve(anyString(), anyString(), anyInt()))
                                .thenReturn(List.of());

                service.execute(command("What is UNADA?"));

                assertThat(ragMetrics.total()).isEqualTo(1);
                assertThat(ragMetrics.noEvidence()).isEqualTo(1);
                assertThat(ragMetrics.llmCalls()).isEqualTo(0);
        }

        // helpers

        private QueryRagCommand command(String query) {
                return new QueryRagCommand(query, TenantId.from("org-test"), null, 3);
        }

        private DocumentChunk chunk(String documentId, double score) {
                return new DocumentChunk(documentId, "org-test", "test content", score);
        }
}