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
 *
 * GET /api/health/jobs → 200 {"status":"OK",...} kad je jutarnji krug poslao sve reveal-e
 *                      → 503 {"status":"REVEAL_KASNI","revealKasni":n,...} kad kupac čeka
 *                        reveal koji je već trebalo da stigne (vidi JobHealthService).
 * Namerno odvojeno od /api/health: taj prati da li server živi, i ne sme da vrati grešku
 * samo zato što jedan mejl kasni.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final com.escapii.service.impl.JobHealthService jobHealthService;

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

    /**
     * Stanje jutarnjeg kruga za spoljni bot. Samo brojevi i datumi - bez imena, mejlova i
     * šifri rezervacija, jer je endpoint javan.
     */
    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> jobs() {
        try {
            com.escapii.service.impl.JobHealthService.Stanje s =
                    jobHealthService.proveri(java.time.LocalDateTime.now());
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status",             s.ok() ? "OK" : "REVEAL_KASNI");
            body.put("revealKasni",        s.revealKasni());
            body.put("bezDestinacije",     s.bezDestinacije());
            body.put("cekaPrognozu",       s.cekaPrognozu());
            body.put("slanjeNijeUspelo",   s.slanjeNijeUspelo());
            body.put("najranijiPolazak",   s.najranijiPolazak() != null ? s.najranijiPolazak().toString() : null);
            body.put("proveravaPolaskeDo", s.proveravaPolaskeDo().toString());
            body.put("timestamp",          Instant.now().toString());
            return ResponseEntity.status(s.ok() ? 200 : 503).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status",    "DOWN",
                    "db",        "ERROR",
                    "timestamp", Instant.now().toString()
            ));
        }
    }
}
