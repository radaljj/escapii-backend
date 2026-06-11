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
}
