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
    private Integer breakfastPerPerson;          // 0 ili 12×noći
    private Integer seatsTogether;               // 0 ili 24 (12€/smer × 2 smera)
    private Integer insurancePerPerson;          // 0 ili 12
    private Integer eurPerPerson;                // zbir svih per-person stavki

    // ── Flat (jedna cena za celu rezervaciju) ─────────────────────────
    /** Ukupna naknada = 10€ × broj naplativih isključivanja × broj putnika. */
    private Integer exclusionCostFlat;
    private Integer soloSurcharge;               // 60€ ako je 1 putnik, inače 0

    // ── Kabinski kofer (selektivan po putniku) ────────────────────────
    private Integer cabinSuitcaseCount;          // koliko putnika je odabralo kofer
    private Integer cabinSuitcaseTotal;          // cabinSuitcaseCount × 80

    // ── Reveal Box (flat po rezervaciji) ─────────────────────────────
    private Integer revealBoxTotal;              // 0 ili 35

    // ── Ukupno ────────────────────────────────────────────────────────
    /** eurPerPerson × n + cabinSuitcaseTotal + exclusionCostFlat + soloSurcharge + revealBoxTotal */
    private Integer totalEurAll;

    // ── Meta ──────────────────────────────────────────────────────────
    private Integer exclusionCount;
    private Integer numberOfTravelers;
    private Integer numberOfNights;
    // ── Promo „besplatno isključivanje" (ExclusionPromo) ──────────────
    /** True kad je uz obračun stigao važeći promo kod: naplativa isključivanja tada koštaju 0. */
    private Boolean exclusionPromoApplied;
    /** Koliko bi isključivanja koštala bez promo koda (0 kad promo nije primenjen ili nema naplativih). */
    private Integer exclusionPromoSavedEur;
    /** Da li promo trenutno traje - sajt tada na koraku isključivanja podseća na kod. Sam kod se NIKAD ne vraća. */
    private Boolean exclusionPromoActive;
}
