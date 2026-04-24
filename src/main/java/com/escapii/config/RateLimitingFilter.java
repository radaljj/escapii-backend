package com.escapii.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter:
 * - POST /api/booking:              max 5 zahteva po IP na sat
 * - GET  /api/booking/price-preview: max 30 zahteva po IP na sat
 * - POST /api/waitlist:             max 5 zahteva po IP na sat
 * - /api/admin/**:                  max 20 zahteva po IP na minut (brute-force zaštita ključa)
 *
 * VAŽNO: X-Forwarded-For se prihvata bez provere — ovo je bezbedno samo ako firewall
 * blokira direktan pristup portu 8080 i sav promet ide kroz reverse proxy (Nginx/Cloudflare).
 * Ako je port 8080 javno dostupan, korisnik može da spoofuje IP.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private static final int  BOOKING_MAX      = 5;
    private static final long BOOKING_WINDOW   = 60 * 60 * 1000L; // 1 sat

    private static final int  PREVIEW_MAX      = 30;
    private static final long PREVIEW_WINDOW   = 60 * 60 * 1000L; // 1 sat

    private static final int  ADMIN_MAX        = 20;
    private static final long ADMIN_WINDOW     = 60 * 1000L;      // 1 minut

    private static final int  WAITLIST_MAX     = 5;
    private static final long WAITLIST_WINDOW  = 60 * 60 * 1000L; // 1 sat

    private final Map<String, Queue<Long>> bookingLog  = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> previewLog  = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> adminLog    = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> waitlistLog = new ConcurrentHashMap<>();

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

        if ("POST".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/api/waitlist")) {
            if (isRateLimited(waitlistLog, ip, WAITLIST_MAX, WAITLIST_WINDOW)) {
                log.warn("[RateLimit] Waitlist limit prekoračen za IP: {}", ip);
                reject(response, "Previše zahteva. Pokušajte ponovo za sat vremena.");
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

    /**
     * Uzima pravi IP korisnika — uzima u obzir X-Forwarded-For header
     * koji Railway/Render/Cloudflare dodaju.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
