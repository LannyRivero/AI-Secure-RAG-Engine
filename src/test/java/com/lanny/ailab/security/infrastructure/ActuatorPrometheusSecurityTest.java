package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.lanny.ailab.testutil.JwtTestBuilder.jwtForTenant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActuatorTestController.class)
@Import({SecurityConfig.class, SecurityAuditService.class, AuditAuthenticationEntryPoint.class, AuditAccessDeniedHandler.class})
@TestPropertySource(properties = "app.security.public-prometheus.enabled=true")
@Tag("acceptance")
class ActuatorPrometheusSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allows_unauthenticated_scrape_for_prometheus_endpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string("prometheus metrics"));
    }

    @Test
    void keeps_other_actuator_paths_protected() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Query-Id"))
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")));
    }

    @Test
    void returns_query_id_for_forbidden_security_responses() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(jwtForTenant("org-test", "ORG_MEMBER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Query-Id"));
    }
}
