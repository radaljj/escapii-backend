package com.escapii.controller;

import com.escapii.model.LaunchSubscriber;
import com.escapii.repository.LaunchSubscriberRepository;
import com.escapii.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Privremeni endpoint za coming-soon stranicu - prikuplja email adrese da
 * obavestimo ljude kad sajt krene live. Uklanja se posle lansiranja.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LaunchSubscriberController {

    private final LaunchSubscriberRepository repository;

    /**
     * POST /api/launch-notify
     * Body: { "email": "..." }
     */
    @PostMapping("/api/launch-notify")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody Map<String, String> body) {
        // Honeypot: boti popune ovo polje, pravi korisnici ne vide ga
        String honeypot = body.getOrDefault("hp", "");
        if (!honeypot.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "subscribed")); // tiho odbaciti
        }

        String email = body.getOrDefault("email", "").trim().toLowerCase();
        if (email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Neispravan email."));
        }

        if (!repository.existsByEmail(email)) {
            try {
                LaunchSubscriber sub = new LaunchSubscriber();
                sub.setEmail(email);
                repository.save(sub);
                log.info("[LaunchNotify] Nova prijava: {}", LogUtils.maskEmail(email));
            } catch (DataIntegrityViolationException e) {
                // race condition - dva istovremena zahteva sa istim emailom, bezopasno
            }
        }

        // Uvek isti odgovor - sprečava enumeraciju prijavljenih email adresa
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }
}
