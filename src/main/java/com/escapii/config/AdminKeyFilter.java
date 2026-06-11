package com.escapii.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Štiti /api/admin/** endpoint-e:
 *  1. Constant-time comparison admin ključa (sprečava timing napad)
 *  2. Rate limit: max 20 pokušaja po IP-u na svakih 10 minuta
 */
@Slf4j
@Component
public class AdminKeyFilter extends OncePerRequestFilter {

    @Value("${app.admin-key}")
    private String adminKey;

    @PostConstruct
    void warnIfDefaultKey() {
        if ("local-dev-only-change-in-production".equals(adminKey)) {
            log.warn("⚠️  [Admin] Koristi se DEFAULT admin ključ! Postavi ADMIN_KEY env varijablu pre produkcije!");
        }
    }

    private static final int    MAX_ATTEMPTS  = 20;
    private static final long   WINDOW_MS     = 10 * 60 * 1000L; // 10 minuta

    private final Map<String, AtomicInteger> attempts   = new ConcurrentHashMap<>();
    private final Map<String, Long>          windowStart = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = getClientIp(request);

        // Rate limit check
        if (isBruteForce(ip)) {
            log.warn("[Admin] Rate limit prekoračen za IP: {}", ip);
            reject(response, 429, "Previše pokušaja. Pokušajte ponovo za 10 minuta.");
            return;
        }

        String provided = request.getHeader("X-Admin-Key");

        // Constant-time comparison (sprečava timing napad)
        if (!constantTimeEquals(adminKey, provided)) {
            recordFailedAttempt(ip);
            log.warn("[Admin] Neispravan ključ sa IP: {}", ip);
            // Konstantno kašnjenje - usporava brute-force i timing napade
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "Pristup odbijen - neispravan admin ključ");
            return;
        }

        // Uspešna autentikacija - resetuj brojač
        attempts.remove(ip);
        windowStart.remove(ip);
        chain.doFilter(request, response);
    }

    private boolean isBruteForce(String ip) {
        long now = System.currentTimeMillis();
        long start = windowStart.getOrDefault(ip, 0L);

        if (now - start > WINDOW_MS) {
            windowStart.put(ip, now);
            attempts.put(ip, new AtomicInteger(0));
            return false;
        }
        return attempts.getOrDefault(ip, new AtomicInteger(0)).get() >= MAX_ATTEMPTS;
    }

    private void recordFailedAttempt(String ip) {
        attempts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        windowStart.putIfAbsent(ip, System.currentTimeMillis());
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private String getClientIp(HttpServletRequest request) {
        return IpUtils.extractClientIp(request);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
