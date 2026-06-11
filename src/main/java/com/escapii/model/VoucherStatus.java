package com.escapii.model;

public enum VoucherStatus {
    /** Kreiran, čeka uplatu kupca. Nije upotrebljiv. */
    PENDING,
    /** Plaćen - admin aktivirao. Spreman za korišćenje. */
    ACTIVE,
    /**
     * Primenjen u aktivnoj rezervaciji (PENDING ili CONFIRMED) -
     * blokiran za dalje korišćenje dok se rezervacija ne završi.
     * Prelazi u USED tek kad rezervacija postane COMPLETED,
     * ili nazad u ACTIVE ako se rezervacija otkaže ili obriše.
     */
    RESERVED,
    /**
     * Trajno iskorišćen - rezervacija završena (COMPLETED).
     * Više se ne može koristiti ni za šta.
     */
    USED,
    /** Prošao rok važenja (1 godina od aktivacije). */
    EXPIRED
}
