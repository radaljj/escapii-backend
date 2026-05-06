package com.escapii.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

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

    /** IPv4: npr. 192.168.1.1 */
    private static final Pattern IPV4 = Pattern.compile(
            "^(\\d{1,3}\\.){3}\\d{1,3}$");

    /** IPv6: kompletna forma i skraćena forma s :: — max 45 znakova */
    private static final Pattern IPV6 = Pattern.compile(
            "^[0-9a-fA-F:]{2,45}$");

    public static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            String ip = parts[parts.length - 1].trim();
            if (isValidIp(ip)) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Vraća true samo za validne IPv4 ili IPv6 adrese.
     * Odbija sve što ne liči na IP (log injection, header spoofing).
     */
    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) return false;
        return IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches();
    }
}
