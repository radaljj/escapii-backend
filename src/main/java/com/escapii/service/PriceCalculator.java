package com.escapii.service;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;

public interface PriceCalculator {

    /**
     * Cenovnik (autoritet: PriceCalculatorImpl):
     *
     *   Po osobi (× n putnika):
     *     baza + Superior? +100 + doručak? +12/noć + sedišta? +24 + osiguranje? +12
     *
     *   Flat (jedna cena za celu rezervaciju):
     *     Reveal Box? +35
     *     Solo doplata (1 putnik)? +60
     *     Isključivanja: ukupno = 10€ × broj naplativih isključivanja × broj putnika
     *
     *   Kabinski kofer (selektivan po putniku):
     *     cabinSuitcaseCount × 100
     *
     * @param departureAirport  IATA kod aerodroma - utiče na pravila isključivanja
     *                          (INI: 0 dozvoljenih; ostali: max 4, 1. gratis, 10€/os za ostale)
     */
    default PricePreviewResponse calculate(
            AvailableDate date,
            int n,
            AccommodationType accommodationType,
            int exclusionCount,
            int cabinSuitcaseCount,
            boolean hasInsurance,
            boolean hasBreakfast,
            boolean hasSeatsTogether,
            boolean hasRevealBox,
            String departureAirport
    ) {
        return calculate(date, n, accommodationType, exclusionCount, cabinSuitcaseCount,
                hasInsurance, hasBreakfast, hasSeatsTogether, hasRevealBox, departureAirport, 0);
    }

    /**
     * Isti obračun, uz promo „besplatna isključivanja destinacija".
     *
     * @param promoFreeExclusions koliko isključivanja UKUPNO ne košta ništa uz važeći promo kod
     *                       (proverava pozivalac, vidi ExclusionPromo); 0 = bez promo koda. Kod
     *                       SKIP3 daje 3: prvo (besplatno i inače), drugo i treće su 0 €, a četvrto
     *                       se naplaćuje. U odgovoru stoji koliko je kupac uštedeo
     *                       ({@code exclusionPromoSavedEur}). Pravila aerodroma (koliko je
     *                       isključivanja dozvoljeno) ostaju ista.
     */
    PricePreviewResponse calculate(
            AvailableDate date,
            int n,
            AccommodationType accommodationType,
            int exclusionCount,
            int cabinSuitcaseCount,
            boolean hasInsurance,
            boolean hasBreakfast,
            boolean hasSeatsTogether,
            boolean hasRevealBox,
            String departureAirport,
            int promoFreeExclusions
    );
}
