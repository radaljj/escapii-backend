package com.escapii.service.impl;

import com.escapii.dto.PricePreviewResponse;
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
 * Sedišta     → +20€/pp (10€/smer × 2 smera)
 * Osiguranje  → +10€/pp
 * <p>
 * Flat (jedna cena za rezervaciju):
 * Isključivanja → 1. besplatno | 2. +10€ | 3. +10€ | 4. +15€ | 5. +15€ (max 50€)
 * <p>
 * Kabinski kofer (selektivan po putniku):
 * +100€/pp (50€/smer × 2 smera)
 */
@Service
public class PriceCalculatorImpl implements PriceCalculator {

    private static final Integer SUPERIOR_PP    = 50;
    private static final Integer PREMIUM_PP     = 130;
    private static final Integer CABIN_SUITCASE = 100;  // 50€/smer × 2 smera
    private static final Integer INSURANCE_PP   = 10;
    private static final Integer BREAKFAST_PP   = 13;
    private static final Integer SEATS_PP       = 20;   // 10€/smer × 2 smera, po osobi
    private static final Integer EXCLUSION_FLAT_LOW  = 10;  // 2. i 3. isključivanje
    private static final Integer EXCLUSION_FLAT_HIGH = 15;  // 4. i 5. isključivanje
    private static final Integer SOLO_SURCHARGE  = 60;  // doplata za solo putnika

    @Override
    public PricePreviewResponse calculate(AvailableDate date, int n, String accommodationType, int exclusionCount, int cabinSuitcaseCount, boolean hasInsurance, boolean hasBreakfast, boolean hasSeatsTogther) {
        int basePrice = date.getBasePrice();
        int accommodationExtra = resolveAccommodationExtra(accommodationType);
        int breakfast = hasBreakfast ? BREAKFAST_PP : 0;
        int seatsTogether = hasSeatsTogther ? SEATS_PP : 0;
        int insurance = hasInsurance ? INSURANCE_PP : 0;

        int exclusionCostFlat = calcExclusionCost(exclusionCount);
        int cabinSuitcaseTotal = cabinSuitcaseCount * CABIN_SUITCASE;
        int soloSurcharge = (n == 1) ? SOLO_SURCHARGE : 0;

        int eurPerPerson = basePrice + accommodationExtra + breakfast + seatsTogether + insurance;
        int totalEurAll = eurPerPerson * n + cabinSuitcaseTotal + exclusionCostFlat + soloSurcharge;

        return PricePreviewResponse.builder()
            .basePricePerPerson(basePrice)
            .accommodationExtraPerPerson(accommodationExtra)
            .breakfastPerPerson(breakfast)
            .seatsTogtherPerPerson(seatsTogether)
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

    /** 1→0€ | 2→10€ | 3→20€ | 4→35€ | 5→50€ */
    private int calcExclusionCost(int n) {
        if (n <= 1) return 0;
        int cost = 0;
        for (int i = 2; i <= n; i++) {
            cost += (i <= 3) ? EXCLUSION_FLAT_LOW : EXCLUSION_FLAT_HIGH;
        }
        return cost;
    }

    private int resolveAccommodationExtra(String type) {
        if (type == null) return 0;
        return switch (type.toUpperCase()) {
            case "SUPERIOR" -> SUPERIOR_PP;
            case "PREMIUM" -> PREMIUM_PP;
            default -> 0;
        };
    }
}
