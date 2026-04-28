package com.escapii.service.impl;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.service.PriceCalculator;
import org.springframework.stereotype.Service;

/**
 * Cenovnik:
 * <p>
 * Po osobi (× n putnika):
 * Osnovna cena  → iz AvailableDate (definisana pri unosu termina)
 * Superior    → +50€/pp
 * Premium     → +130€/pp
 * Doručak     → +13€/pp
 * Sedišta     → +24€/pp (12€/smer × 2 smera)
 * Osiguranje  → +12€/pp
 * <p>
 * Flat (jedna cena za rezervaciju):
 * BEG/ostali → 1. besplatno | 2. +10€ | 3. +10€ | 4. +15€ | 5. +15€ (max 50€)
 * INI        → 1. besplatno | 2. +15€ (max 2 isključivanja)
 * <p>
 * Kabinski kofer (selektivan po putniku):
 * +100€/pp (50€/smer × 2 smera)
 */
@Service
public class PriceCalculatorImpl implements PriceCalculator {

    public static final Integer SUPERIOR_PP    = 50;
    public static final Integer PREMIUM_PP     = 130;
    private static final Integer CABIN_SUITCASE = 100;  // 50€/smer × 2 smera
    public static final Integer INSURANCE_PP   = 12;
    public static final Integer BREAKFAST_PP   = 13;
    public static final Integer SEATS_PP       = 24;   // 12€/smer × 2 smera, po osobi
    public static final Integer CABIN_SUITCASE_PRICE = 100;  // 50€/smer × 2 smera
    private static final Integer EXCLUSION_FLAT_LOW  = 10;  // 2. i 3. isključivanje
    private static final Integer EXCLUSION_FLAT_HIGH = 15;  // 4. i 5. isključivanje
    private static final Integer SOLO_SURCHARGE  = 60;  // doplata za solo putnika

    @Override
    public PricePreviewResponse calculate(AvailableDate date, int n, AccommodationType accommodationType, int exclusionCount, int cabinSuitcaseCount, boolean hasInsurance, boolean hasBreakfast, boolean hasSeatsTogether) {
        int basePrice = date.getBasePrice();
        int accommodationExtra = resolveAccommodationExtra(accommodationType);
        int breakfast = hasBreakfast ? BREAKFAST_PP : 0;
        int seatsTogether = hasSeatsTogether ? SEATS_PP : 0;
        int insurance = hasInsurance ? INSURANCE_PP : 0;

        int exclusionCostFlat = calcExclusionCost(exclusionCount, date.getDepartureAirport());
        int cabinSuitcaseTotal = cabinSuitcaseCount * CABIN_SUITCASE;
        int soloSurcharge = (n == 1) ? SOLO_SURCHARGE : 0;

        int eurPerPerson = basePrice + accommodationExtra + breakfast + seatsTogether + insurance;
        int totalEurAll = eurPerPerson * n + cabinSuitcaseTotal + exclusionCostFlat + soloSurcharge;

        return PricePreviewResponse.builder()
            .basePricePerPerson(basePrice)
            .accommodationExtraPerPerson(accommodationExtra)
            .breakfastPerPerson(breakfast)
            .seatsTogether(seatsTogether)
            .insurancePerPerson(insurance)
            .eurPerPerson(eurPerPerson)
            .exclusionCostFlat(exclusionCostFlat)
            .soloSurcharge(soloSurcharge)
            .cabinSuitcaseCount(cabinSuitcaseCount)
            .cabinSuitcaseTotal(cabinSuitcaseTotal)
            .totalEurAll(totalEurAll)
            .exclusionCount(exclusionCount)
            .numberOfTravelers(n)
            .numberOfNights(date.getNumberOfNights())
            .build();
    }

    /**
     * BEG/ostali: 1→0€ | 2→10€ | 3→20€ | 4→35€ | 5→50€
     * INI:        1→0€ | 2→15€ (max 2)
     */
    private int calcExclusionCost(int n, String airport) {
        if (n <= 1) return 0;
        if ("INI".equalsIgnoreCase(airport)) {
            // Za Niš: samo 2 isključivanja, 1. gratis, 2. +15€
            return Math.min(n - 1, 1) * EXCLUSION_FLAT_HIGH;
        }
        int cost = 0;
        for (int i = 2; i <= n; i++) {
            cost += (i <= 3) ? EXCLUSION_FLAT_LOW : EXCLUSION_FLAT_HIGH;
        }
        return cost;
    }

    private int resolveAccommodationExtra(AccommodationType type) {
        if (type == null) return 0;
        return switch (type) {
            case SUPERIOR -> SUPERIOR_PP;
            case PREMIUM  -> PREMIUM_PP;
            default       -> 0;
        };
    }
}
