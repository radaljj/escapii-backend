package com.escapii.util;

/**
 * Utility metode za bezbedno logovanje - sprečava curenje PII u log fajlove (GDPR).
 */
public final class LogUtils {

    private LogUtils() {}

    /**
     * Maskira email adresu za logovanje.
     * "marko.petrovic@gmail.com" → "ma***@gmail.com"
     * Čuva domein radi dijagnostike (da li je problem sa određenim provajderom),
     * ali skriva lokalni deo koji identifikuje korisnika.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local  = email.substring(0, at);
        String domain = email.substring(at);          // uključuje @
        String visible = local.length() <= 2
                ? local.substring(0, 1) + "***"
                : local.substring(0, 2) + "***";
        return visible + domain;
    }

    /** Maskira bearer token (private-date, reveal) - vidljivo samo prvih 8 znakova. */
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) return "***";
        return token.length() <= 8 ? "***" : token.substring(0, 8) + "...";
    }

    /** Maskira vaučer kod - vidljivo samo "ESC-" (ili analogni prefix do prve crtice). */
    public static String maskVoucherCode(String code) {
        if (code == null || code.length() < 4) return "***";
        int firstDash = code.indexOf('-');
        return firstDash > 0 ? code.substring(0, firstDash + 1) + "****" : "****";
    }
}
