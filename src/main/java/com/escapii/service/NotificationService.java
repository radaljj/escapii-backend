package com.escapii.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time notifikacije za admin panel via SSE.
 * Fetch-based pristup — admin key ide u header, nikad u URL.
 *
 * Heartbeat svakih 25s drži konekciju živom kroz Nginx (koji ubija
 * idle konekcije posle ~60s ako nema podataka).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // 0 = bez timeout-a na nivou Spring-a; Nginx/browser sami upravljaju konekcijom
    private static final long SSE_TIMEOUT_MS = 0L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ── Konekcija ──────────────────────────────────────────────────────────────

    public SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(()    -> { emitters.remove(id); emitter.complete(); });
        emitter.onError(e      -> emitters.remove(id));

        emitters.put(id, emitter);
        log.debug("SSE konekcija otvorena: {} (ukupno: {})", id, emitters.size());

        // Heartbeat odmah da browser zna da je konekcija živa
        try {
            emitter.send(SseEmitter.event().name("ping").data("ok"));
        } catch (IOException e) {
            emitters.remove(id);
        }

        return emitter;
    }

    // ── Heartbeat — sprečava Nginx/proxy da ubije idle konekciju ──────────────

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data("ok"));
            } catch (Exception e) {
                emitters.remove(id);
                log.debug("SSE heartbeat: uklonjena dead konekcija {}", id);
            }
        });
    }

    // ── Push notifikacija ──────────────────────────────────────────────────────

    public void push(String type, String title, String body) {
        if (emitters.isEmpty()) return;

        String json = String.format(
            "{\"type\":\"%s\",\"title\":\"%s\",\"body\":\"%s\",\"ts\":%d}",
            escape(type), escape(title), escape(body), System.currentTimeMillis()
        );

        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("notification").data(json));
            } catch (Exception e) {
                emitters.remove(id);
            }
        });

        log.info("SSE push [{}] → {} → {}", type, title, body);
    }

    // ── Shorthand metode ───────────────────────────────────────────────────────

    public void newBooking(String ref, String name, int total) {
        push("booking", "Nova rezervacija", ref + " · " + name + " · " + total + "€");
    }

    public void newInquiry(String email, String airport) {
        push("inquiry", "Novi custom upit", email + " · " + airport);
    }

    public void newWaitlist(String email, String airport) {
        push("waitlist", "Nova waitlista", email + " · " + airport);
    }

    public void newPrivateLink(String airport, String departureDate) {
        push("private", "Privatni termin kreiran", airport + " · " + departureDate);
    }

    public void newDate(String airport, String departureDate) {
        push("date", "Novi termin dodat", airport + " · polazak " + departureDate);
    }

    public void bookingConfirmed(String ref, String name) {
        push("confirmed", "Rezervacija potvrđena", ref + " · " + name);
    }

    public void bookingCancelled(String ref, String reason) {
        push("cancelled", "Rezervacija otkazana", ref + " · " + reason);
    }

    public void appError(String endpoint, int status) {
        push("error", "Greška u aplikaciji", status + " · " + endpoint);
    }

    // ── Utils ──────────────────────────────────────────────────────────────────

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
