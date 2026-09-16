package com.escapii.model;

/**
 * Stanje zbirne fakture Escapii → agencija.
 * <pre>
 *   SENT ⇄ PAID     (uplata / rollback uplate)
 *   SENT → VOIDED   (storno: broj ostaje u istoriji, rezervacije iz nje se vraćaju
 *                    u red i ulaze u sledeću fakturu)
 * </pre>
 * PAID → VOIDED nije dozvoljeno direktno: prvo rollback uplate.
 */
public enum AgencyInvoiceStatus {
    SENT,
    PAID,
    VOIDED
}
