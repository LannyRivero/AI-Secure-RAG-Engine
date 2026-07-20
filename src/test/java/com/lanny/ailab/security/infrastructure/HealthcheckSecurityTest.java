package com.lanny.ailab.security.infrastructure;

import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import com.lanny.ailab.shared.infrastructure.web.HealthcheckController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthcheckController.class)
@Import({SecurityConfig.class, SecurityAuditService.class, AuditAuthenticationEntryPoint.class, AuditAccessDeniedHandler.class})
@Tag("acceptance")
class HealthcheckSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allows_unauthenticated_platform_health_checks() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"up\"}"));
    }
}
