package com.escapii.controller;

import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    // ── Javni endpoint ────────────────────────────────────────────────────────

    /**
     * POST /api/waitlist
     * Body: { "email": "...", "airport": "BEG" }
     */
    private static final Set<String> VALID_AIRPORTS = Set.of("BEG", "INI", "ZAG", "BUD", "TIM");

    @PostMapping("/api/waitlist")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody Map<String, String> body) {
        // Honeypot: boti popune ovo polje, pravi korisnici ne vide ga
        String honeypot = body.getOrDefault("hp", "");
        if (!honeypot.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "subscribed")); // tiho odbaciti
        }

        String email   = body.getOrDefault("email",   "").trim().toLowerCase();
        String airport = body.getOrDefault("airport", "").trim().toUpperCase();

        if (email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 200) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Neispravan email."));
        }
        if (!VALID_AIRPORTS.contains(airport)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nepoznat aerodrom."));
        }

        // Uvek vraćamo isti odgovor — sprečava enumeraciju registrovanih email adresa
        waitlistService.subscribe(email, airport);
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }

    // ── Admin endpointi ───────────────────────────────────────────────────────

    /** GET /api/admin/waitlist — lista svih čekajućih po aerodromu. */
    @GetMapping("/api/admin/waitlist")
    public ResponseEntity<Map<String, Object>> getWaitlist() {
        return ResponseEntity.ok(waitlistService.getWaitlistSummary());
    }

    /**
     * POST /api/admin/waitlist/notify/{airport}
     * Šalje email svim čekajućima za dati aerodrom i briše ih sa liste.
     */
    @PostMapping("/api/admin/waitlist/notify/{airport}")
    public ResponseEntity<Map<String, Object>> notifyAndClear(@PathVariable String airport) {
        String ap = airport.trim().toUpperCase();
        if (!VALID_AIRPORTS.contains(ap)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nepoznat aerodrom: " + ap));
        }
        int sent = waitlistService.notifyAndClear(ap);

        if (sent == 0) {
            return ResponseEntity.ok(Map.of("sent", 0, "message", "Nema čekajućih za " + ap));
        }
        return ResponseEntity.ok(Map.of("sent", sent, "message", "Poslato " + sent + " notifikacija."));
    }
}
