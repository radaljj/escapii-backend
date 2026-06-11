package com.escapii.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Javni health check endpoint - ne zahteva autentifikaciju.
 * Koristi se za UptimeRobot monitoring i load balancer health probe.
 *
 * GET /api/health → 200 {"status":"UP","timestamp":"..."}
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",    "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
