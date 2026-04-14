package com.escapii.dto;

import lombok.*;

/**
 * Odgovor na GET /api/booking/price-preview.
 * Sve cene su u EUR (celi brojevi).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricePreviewResponse {

    // ── Po osobi × n (važi za sve putnike) ───────────────────────────
    private Integer basePricePerPerson;
    private Integer accommodationExtraPerPerson; // 0, 50 (Superior) ili 130 (Premium)
    private Integer breakfastPerPerson;          // 0 ili 15
    private Integer seatsTogtherPerPerson;       // 0 ili 10
    private Integer insurancePerPerson;          // 0 ili 15
    private Integer eurPerPerson;                // zbir svih per-person stavki

    // ── Flat (jedna cena za celu rezervaciju) ─────────────────────────
    private Integer exclusionCostFlat;           // 0, 10 ili 20 (2. i 3. isključivanje)
    private Integer soloSurcharge;               // 60€ ako je 1 putnik, inače 0

    // ── Kabinski kofer (selektivan po putniku) ────────────────────────
    private Integer cabinSuitcaseCount;          // koliko putnika je odabralo kofer
    private Integer cabinSuitcaseTotal;          // cabinSuitcaseCount × 80

    // ── Ukupno ────────────────────────────────────────────────────────
    /** eurPerPerson × n + cabinSuitcaseTotal + exclusionCostFlat + soloSurcharge */
    private Integer totalEurAll;

    // ── Meta ──────────────────────────────────────────────────────────
    private Integer exclusionCount;
    private Integer numberOfTravelers;
    private Integer numberOfNights;
}
