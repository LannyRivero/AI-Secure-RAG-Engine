package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.security.infrastructure.SecurityConfig;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

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

    @Test
    void returns_401_when_request_has_no_jwt() throws Exception {
        mockMvc.perform(get("/rag/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_200_with_all_metric_fields() throws Exception {
        when(ragMetrics.total()).thenReturn(10.0);
        when(ragMetrics.rejected()).thenReturn(2.0);
        when(ragMetrics.llmCalls()).thenReturn(8.0);
        when(ragMetrics.noEvidence()).thenReturn(1.0);

        mockMvc.perform(get("/rag/metrics")
                        .with(jwtForTenant("org-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10.0))
                .andExpect(jsonPath("$.rejected").value(2.0))
                .andExpect(jsonPath("$.llmCalls").value(8.0))
                .andExpect(jsonPath("$.noEvidence").value(1.0));
    }

    @Test
    void returns_200_with_zero_metrics_when_no_requests_processed() throws Exception {
        when(ragMetrics.total()).thenReturn(0.0);
        when(ragMetrics.rejected()).thenReturn(0.0);
        when(ragMetrics.llmCalls()).thenReturn(0.0);
        when(ragMetrics.noEvidence()).thenReturn(0.0);

        mockMvc.perform(get("/rag/metrics")
                        .with(jwtForTenant("org-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0));
    }
}
