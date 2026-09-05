package com.escapii.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Zajednička logika za ekstrakciju pravog client IP-a.
 *
 * <p>Redosled izvora nije proizvoljan:
 *
 * <ol>
 *   <li><b>CF-Connecting-IP</b> — Cloudflare ga postavlja na svakom zahtevu i
 *       <i>prepisuje</i> vrednost koju je klijent poslao. Kroz Cloudflare se zato ne
 *       može lažirati, dok se X-Forwarded-For može napuniti unapred.</li>
 *   <li><b>Poslednji unos iz X-Forwarded-For</b> — rezerva kad zahtev ne ide kroz
 *       Cloudflare (lokalni razvoj, poziv sa samog servera). Poslednji jer proxy dopisuje
 *       pravu adresu na kraj, pa je klijent ne može pomeriti stavljanjem lažne ispred.</li>
 *   <li>remoteAddr — direktan peer, kad nema nijednog zaglavlja.</li>
 * </ol>
 *
 * <p><b>Ovo vredi samo dok je origin zatvoren.</b> Nijedno zaglavlje nije dokaz samo po
 * sebi: ko god otvori vezu direktno ka serveru, mimo Cloudflare-a, može poslati i
 * CF-Connecting-IP i X-Forwarded-For po želji, i time zaobići svaku zaštitu koja se meri
 * po IP-u - rate limit rezervacija, brojač pogrešnih admin ključeva, limit na vaučere.
 * To stvarno sprečava jedino firewall koji na portovima 80/443 pušta samo Cloudflare
 * opsege. Ako se ta pravila ikad uklone, ova klasa je opet samo pretpostavka.
 *
 * <p>Ranija verzija je gledala isključivo poslednji X-Forwarded-For unos, jer je aplikacija
 * bila na Renderu gde je platformski proxy dopisivao pravu adresu poslednji. Infrastruktura
 * je sada Cloudflare -> nginx -> Spring, a nginx X-Forwarded-For uopšte ne dira.
 */
public final class IpUtils {

    private IpUtils() {}

    /** Zaglavlje u koje Cloudflare upisuje pravu adresu posetioca. */
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    /** IPv4: npr. 192.168.1.1 */
    private static final Pattern IPV4 = Pattern.compile(
            "^(\\d{1,3}\\.){3}\\d{1,3}$");

    /** IPv6: kompletna forma i skraćena forma s :: - max 45 znakova */
    private static final Pattern IPV6 = Pattern.compile(
            "^[0-9a-fA-F:]{2,45}$");

    public static String extractClientIp(HttpServletRequest request) {
        // Cloudflare prepisuje ovo zaglavlje svojom vrednošću i briše ono što je klijent
        // poslao - zato ima primat nad X-Forwarded-For, koji se može napuniti unapred.
        String cf = request.getHeader(CF_CONNECTING_IP);
        if (cf != null && !cf.isBlank()) {
            String cfIp = cf.trim();
            if (isValidIp(cfIp)) {
                return cfIp;
            }
        }

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
