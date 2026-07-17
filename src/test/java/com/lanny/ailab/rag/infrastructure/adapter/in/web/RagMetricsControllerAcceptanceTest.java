package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.security.infrastructure.SecurityConfig;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.lanny.ailab.testutil.JwtTestBuilder.jwtForTenant;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagMetricsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@Tag("acceptance")
class RagMetricsControllerAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagMetrics ragMetrics;

    @MockitoBean
    private IngestionMetrics ingestionMetrics;

    @Test
    @DisplayName("When a request has no JWT, the API returns 401 Unauthorized")
    void returns_401_when_request_has_no_jwt() throws Exception {
        mockMvc.perform(get("/rag/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("When a request is authenticated as an org member, the API returns 403 Forbidden")
    void returns_403_when_authenticated_as_org_member() throws Exception {
        mockMvc.perform(get("/rag/metrics")
                        .with(jwtForTenant("org-test", "ORG_MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("When a request is authenticated as a platform admin, the API returns 200 OK with metrics")
    void returns_200_with_all_metric_fields_when_authenticated_as_platform_admin() throws Exception {
        when(ragMetrics.total()).thenReturn(10.0);
        when(ragMetrics.rejected()).thenReturn(2.0);
        when(ragMetrics.llmCalls()).thenReturn(8.0);
        when(ragMetrics.noEvidence()).thenReturn(1.0);
        when(ingestionMetrics.accepted()).thenReturn(12.0);
        when(ingestionMetrics.completed()).thenReturn(10.0);
        when(ingestionMetrics.retriesScheduled()).thenReturn(3.0);
        when(ingestionMetrics.failed()).thenReturn(4.0);
        when(ingestionMetrics.deadLettered()).thenReturn(1.0);
        when(ingestionMetrics.queued()).thenReturn(2.0);
        when(ingestionMetrics.processing()).thenReturn(1.0);
        when(ingestionMetrics.failedQueue()).thenReturn(0.0);
        when(ingestionMetrics.deadLetterQueue()).thenReturn(1.0);

        mockMvc.perform(get("/rag/metrics")
                        .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10.0))
                .andExpect(jsonPath("$.rejected").value(2.0))
                .andExpect(jsonPath("$.llmCalls").value(8.0))
                .andExpect(jsonPath("$.noEvidence").value(1.0))
                .andExpect(jsonPath("$.ingestionAccepted").value(12.0))
                .andExpect(jsonPath("$.ingestionCompleted").value(10.0))
                .andExpect(jsonPath("$.ingestionRetriesScheduled").value(3.0))
                .andExpect(jsonPath("$.ingestionFailed").value(4.0))
                .andExpect(jsonPath("$.ingestionDeadLettered").value(1.0))
                .andExpect(jsonPath("$.ingestionQueued").value(2.0))
                .andExpect(jsonPath("$.ingestionProcessing").value(1.0))
                .andExpect(jsonPath("$.ingestionFailedQueue").value(0.0))
                .andExpect(jsonPath("$.ingestionDeadLetterQueue").value(1.0));
    }

    @Test
    @DisplayName("When no requests have been processed, the API returns 200 OK with zero metrics")
    void returns_200_with_zero_metrics_when_no_requests_processed() throws Exception {
        when(ragMetrics.total()).thenReturn(0.0);
        when(ragMetrics.rejected()).thenReturn(0.0);
        when(ragMetrics.llmCalls()).thenReturn(0.0);
        when(ragMetrics.noEvidence()).thenReturn(0.0);
        when(ingestionMetrics.accepted()).thenReturn(0.0);
        when(ingestionMetrics.completed()).thenReturn(0.0);
        when(ingestionMetrics.retriesScheduled()).thenReturn(0.0);
        when(ingestionMetrics.failed()).thenReturn(0.0);
        when(ingestionMetrics.deadLettered()).thenReturn(0.0);
        when(ingestionMetrics.queued()).thenReturn(0.0);
        when(ingestionMetrics.processing()).thenReturn(0.0);
        when(ingestionMetrics.failedQueue()).thenReturn(0.0);
        when(ingestionMetrics.deadLetterQueue()).thenReturn(0.0);

        mockMvc.perform(get("/rag/metrics")
                        .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.ingestionQueued").value(0.0));
    }
}
