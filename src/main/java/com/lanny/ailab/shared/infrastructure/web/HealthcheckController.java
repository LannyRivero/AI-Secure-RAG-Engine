package com.lanny.ailab.shared.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal unauthenticated liveness endpoint for platform health checks.
 *
 * <p>This endpoint exists so staging and production platforms can verify that the
 * process is accepting HTTP traffic without requiring a JWT for every probe.</p>
 */
@RestController
public class HealthcheckController {

    /**
     * Returns a lightweight liveness payload for platform health checks.
     *
     * @return a constant status payload indicating the process is up
     */
    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "up");
    }
}
