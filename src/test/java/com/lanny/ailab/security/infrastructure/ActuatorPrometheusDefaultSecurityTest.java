package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActuatorTestController.class)
@Import({SecurityConfig.class, SecurityAuditService.class, AuditAuthenticationEntryPoint.class, AuditAccessDeniedHandler.class})
@Tag("acceptance")
class ActuatorPrometheusDefaultSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/prometheus - returns 401 when no JWT is provided")
    void keeps_prometheus_protected_by_default() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
