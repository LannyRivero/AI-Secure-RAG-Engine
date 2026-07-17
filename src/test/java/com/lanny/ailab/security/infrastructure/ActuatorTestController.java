package com.lanny.ailab.security.infrastructure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ActuatorTestController {

    @GetMapping("/actuator/prometheus")
    String prometheus() {
        return "prometheus metrics";
    }

    @GetMapping("/actuator/health")
    String health() {
        return "healthy";
    }
}
