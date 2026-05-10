package com.escapii.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter:
 * - POST /api/booking:               max 5 zahteva po IP na sat
 * - GET  /api/booking/price-preview:  max 30 zahteva po IP na sat
 * - GET  /api/booking/status:         max 5 zahteva po IP na 15 minuta
 * - POST /api/waitlist:              max 5 zahteva po IP na sat
 * - GET  /api/dates:                 max 60 zahteva po IP na minut
 * - GET  /api/destinations/**:        max 60 zahteva po IP na minut (/all, /countries, itd.)
 * - /api/admin/**:                   max 20 zahteva po IP na minut (brute-force zaštita ključa)
 * - GET  /api/reveal:                max 10 zahteva po IP na 15 minuta
 * - POST /api/reveal/confirm:        max 10 zahteva po IP na 15 minuta
 * - POST /api/inquiries/custom-date: max 3 zahteva po IP na sat
 *
 * IP ekstrakcija: uzima POSLEDNJI unos iz X-Forwarded-For — taj dodaje naš trusted proxy
 * (Render/Railway), pa korisnik ne može da ga spoofuje stavljanjem lažnog IP-a ispred.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private static final int  BOOKING_MAX      = 5;
    private static final long BOOKING_WINDOW   = 60 * 60 * 1000L;      // 1 sat

    private static final int  PREVIEW_MAX      = 30;
    private static final long PREVIEW_WINDOW   = 60 * 60 * 1000L;      // 1 sat

    private static final int  STATUS_MAX       = 5;
    private static final long STATUS_WINDOW    = 15 * 60 * 1000L;      // 15 minuta

    private static final int  ADMIN_MAX        = 20;
    private static final long ADMIN_WINDOW     = 60 * 1000L;           // 1 minut

    private static final int  WAITLIST_MAX     = 5;
    private static final long WAITLIST_WINDOW  = 60 * 60 * 1000L;      // 1 sat

    private static final int  DATES_MAX        = 60;
    private static final long DATES_WINDOW     = 60 * 1000L;           // 1 minut

    private static final int  DESTINATIONS_MAX    = 60;
    private static final long DESTINATIONS_WINDOW = 60 * 1000L;        // 1 minut

    private static final int  REVEAL_MAX         = 10;
    private static final long REVEAL_WINDOW      = 15 * 60 * 1000L;   // 15 minuta

    private static final int  INQUIRY_MAX        = 3;
    private static final long INQUIRY_WINDOW     = 60 * 60 * 1000L;   // 1 sat

    // Maksimalni prozor — za cleanup: ne čuvamo ništa starije od ovoga
    private static final long MAX_WINDOW       = 60 * 60 * 1000L;      // 1 sat

    private final Map<String, Queue<Long>> bookingLog      = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> previewLog      = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> statusLog       = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> adminLog        = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> waitlistLog     = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> datesLog        = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> destinationsLog = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> revealLog       = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> inquiryLog      = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        String ip = extractClientIp(request);

        if ("POST".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/api/booking")) {
            if (isRateLimited(bookingLog, ip, BOOKING_MAX, BOOKING_WINDOW)) {
                log.warn("[RateLimit] Booking limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Možete poslati maksimalno 5 upita po satu.");
                return;
            }
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.contains("/api/booking/price-preview")) {
            if (isRateLimited(previewLog, ip, PREVIEW_MAX, PREVIEW_WINDOW)) {
                log.warn("[RateLimit] Price-preview limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva.");
                return;
            }
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.contains("/api/booking/status")) {
            if (isRateLimited(statusLog, ip, STATUS_MAX, STATUS_WINDOW)) {
                log.warn("[RateLimit] Status limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Pokušajte ponovo za 15 minuta.");
                return;
            }
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.equals("/api/dates")) {
            if (isRateLimited(datesLog, ip, DATES_MAX, DATES_WINDOW)) {
                log.warn("[RateLimit] Dates limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva.");
                return;
            }
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.startsWith("/api/destinations")) {
            if (isRateLimited(destinationsLog, ip, DESTINATIONS_MAX, DESTINATIONS_WINDOW)) {
                log.warn("[RateLimit] Destinations limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva.");
                return;
            }
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/api/waitlist")) {
            if (isRateLimited(waitlistLog, ip, WAITLIST_MAX, WAITLIST_WINDOW)) {
                log.warn("[RateLimit] Waitlist limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Pokušajte ponovo za sat vremena.");
                return;
            }
        }

        if (uri.startsWith("/api/reveal")) {
            if (isRateLimited(revealLog, ip, REVEAL_MAX, REVEAL_WINDOW)) {
                log.warn("[RateLimit] Reveal limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Pokušajte ponovo za 15 minuta.");
                return;
            }
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/api/inquiries/custom-date")) {
            if (isRateLimited(inquiryLog, ip, INQUIRY_MAX, INQUIRY_WINDOW)) {
                log.warn("[RateLimit] Inquiry limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Možete poslati maksimalno 3 upita po satu.");
                return;
            }
        }

        if (uri.startsWith("/api/admin")) {
            if (isRateLimited(adminLog, ip, ADMIN_MAX, ADMIN_WINDOW)) {
                log.warn("[RateLimit] Admin limit prekoračen za IP: {}", ip);
                reject(response, "Previše admin zahteva. Sačekajte minut.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(Map<String, Queue<Long>> log, String ip, int max, long windowMs) {
        long now = System.currentTimeMillis();
        Queue<Long> timestamps = log.computeIfAbsent(ip, k -> new LinkedList<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peek() > windowMs) {
                timestamps.poll();
            }
            if (timestamps.size() >= max) return true;
            timestamps.offer(now);
            return false;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private String extractClientIp(HttpServletRequest request) {
        return IpUtils.extractClientIp(request);
    }

    /**
     * Periodično čisti stare unose iz rate-limit mapa — svakih sat vremena.
     * Sprečava neograničen rast memorije pri velikom broju unikatnih IP-ova.
     */
    @Scheduled(fixedRate = 3_600_000) // svakih sat vremena
    public void evictStaleEntries() {
        long cutoff = System.currentTimeMillis() - MAX_WINDOW;
        for (Map<String, Queue<Long>> logMap : new Map[]{bookingLog, previewLog, statusLog, adminLog, waitlistLog, datesLog, destinationsLog, revealLog, inquiryLog}) {
            Iterator<Map.Entry<String, Queue<Long>>> it = logMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Queue<Long>> entry = it.next();
                Queue<Long> q = entry.getValue();
                synchronized (q) {
                    // Ukloni stare timestampove
                    while (!q.isEmpty() && q.peek() < cutoff) q.poll();
                    // Ako je red prazan, ukloni IP iz mape
                    if (q.isEmpty()) it.remove();
                }
            }
        }
        log.debug("[RateLimit] Eviction završena. Aktivnih IP-ova: booking={}, preview={}, status={}, admin={}, waitlist={}, dates={}, destinations={}, reveal={}, inquiry={}",
                bookingLog.size(), previewLog.size(), statusLog.size(), adminLog.size(), waitlistLog.size(),
                datesLog.size(), destinationsLog.size(), revealLog.size(), inquiryLog.size());
    }
}
