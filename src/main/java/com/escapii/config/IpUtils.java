package com.escapii.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Zajednička logika za ekstrakciju pravog client IP-a.
 *
 * Uzima POSLEDNJI unos iz X-Forwarded-For — taj dodaje naš trusted reverse proxy
 * (Render/Railway), pa klijent ne može da ga spoofuje stavljanjem lažnog IP-a ispred.
 *
 * Primer: klijent šalje "X-Forwarded-For: fake-ip"
 *         Render dodaje:  "X-Forwarded-For: fake-ip, 5.6.7.8"  ← 5.6.7.8 je pravi IP
 *         Vraćamo:        "5.6.7.8"
 */
public final class IpUtils {

    private IpUtils() {}

    public static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            String ip = parts[parts.length - 1].trim();
            // Osnovna sanacija: prihvati samo ako liči na IP (IPv4/IPv6, max 45 chars)
            if (!ip.isEmpty() && ip.length() <= 45) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
