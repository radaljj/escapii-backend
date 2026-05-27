package com.escapii.model;

public enum VoucherStatus {
    /** Kreiran, čeka uplatu kupca. Nije upotrebljiv. */
    PENDING,
    /** Plaćen — admin aktivirao. Spreman za korišćenje. */
    ACTIVE,
    /**
     * Primenjen u pending rezervaciji — blokiran za dalje korišćenje,
     * ali još nije konačno iskorišćen. Prelazi u USED kad rezervacija
     * postane CONFIRMED, ili nazad u ACTIVE ako se rezervacija otkaže.
     */
    RESERVED,
    /** Konačno iskorišćen — rezervacija potvrđena (CONFIRMED). */
    USED,
    /** Prošao rok važenja (1 godina od aktivacije). */
    EXPIRED
}
