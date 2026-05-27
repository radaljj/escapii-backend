package com.escapii.model;

public enum VoucherStatus {
    /** Kreiran, čeka uplatu kupca. Nije upotrebljiv. */
    PENDING,
    /** Plaćen — admin aktivirao. Spreman za korišćenje. */
    ACTIVE,
    /** Iskorišćen u booking-u. */
    USED,
    /** Prošao rok važenja (1 godina od aktivacije). */
    EXPIRED
}
