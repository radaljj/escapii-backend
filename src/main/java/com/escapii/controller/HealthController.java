package com.escapii.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Javni health check endpoint - ne zahteva autentifikaciju.
 * Koristi se za UptimeRobot monitoring i load balancer health probe.
 *
 * GET /api/health → 200 {"status":"UP","db":"OK","timestamp":"..."}
 *                 → 503 {"status":"DOWN","db":"ERROR","timestamp":"..."}
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of(
                    "status",    "UP",
                    "db",        "OK",
                    "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status",    "DOWN",
                    "db",        "ERROR",
                    "timestamp", Instant.now().toString()
            ));
        }
    }
}
