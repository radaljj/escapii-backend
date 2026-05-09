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
 * Superior    → +100€/pp
 * Doručak     → +20€/pp
 * Sedišta     → +24€/pp (12€/smer × 2 smera)
 * Osiguranje  → +12€/pp
 * <p>
 * Isključivanja (max 4 za sve aerodrome):
 * 1. besplatno | 2. +15€/pp | 3. +15€/pp | 4. +15€/pp
 * <p>
 * Kabinski kofer (selektivan po putniku):
 * +100€/pp (50€/smer × 2 smera)
 */
@Service
public class PriceCalculatorImpl implements PriceCalculator {

    public static final Integer SUPERIOR_PP      = 100;
    private static final Integer CABIN_SUITCASE  = 100;  // 50€/smer × 2 smera
    public static final Integer INSURANCE_PP     = 12;
    public static final Integer BREAKFAST_PP     = 20;
    public static final Integer SEATS_PP         = 24;   // 12€/smer × 2 smera, po osobi
    private static final Integer EXCLUSION_PP    = 15;   // po osobi, za 2., 3. i 4. isključivanje
    private static final Integer SOLO_SURCHARGE  = 60;   // doplata za solo putnika

    @Override
    public PricePreviewResponse calculate(AvailableDate date, int n, AccommodationType accommodationType, int exclusionCount, int cabinSuitcaseCount, boolean hasInsurance, boolean hasBreakfast, boolean hasSeatsTogether) {
        int basePrice = date.getBasePrice();
        int accommodationExtra = resolveAccommodationExtra(accommodationType);
        int breakfast = hasBreakfast ? BREAKFAST_PP : 0;
        int seatsTogether = hasSeatsTogether ? SEATS_PP : 0;
        int insurance = hasInsurance ? INSURANCE_PP : 0;

        // Isključivanja: 1. gratis, 2. i 3. → 15€/pp svako
        int exclusionCostFlat = calcExclusionCost(exclusionCount, n);
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
     * Sve aerodrome: max 4 isključivanja.
     * 1. → 0€ | 2. → +15€/pp | 3. → +15€/pp | 4. → +15€/pp
     */
    private int calcExclusionCost(int exclusionCount, int n) {
        if (exclusionCount <= 1) return 0;
        return Math.min(exclusionCount - 1, 3) * EXCLUSION_PP * n;
    }

    private int resolveAccommodationExtra(AccommodationType type) {
        if (type == null) return 0;
        return switch (type) {
            case SUPERIOR -> SUPERIOR_PP;
            default       -> 0;
        };
    }
}
