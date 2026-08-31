package com.escapii.service.impl;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.model.DepartureAirport;
import com.escapii.service.PriceCalculator;
import org.springframework.stereotype.Service;

/**
 * Cenovnik:
 * <p>
 * Po osobi (× n putnika):
 * Osnovna cena  → iz AvailableDate (definisana pri unosu termina)
 * Superior    → +100€/pp
 * Doručak     → +12€/pp
 * Sedišta     → +24€/pp (12€/smer × 2 smera)
 * Osiguranje  → +12€/pp
 * <p>
 * Isključivanja: +10€/pp po isključenju, ali koliko ih je dozvoljeno i da li je
 * prvo besplatno definiše sam aerodrom (DepartureAirport) - npr. BEG i BUD imaju
 * 4 sa prvim gratis, INI nijedno (dostupnost letova).
 * <p>
 * Kabinski kofer (selektivan po putniku):
 * +100€/pp (50€/smer × 2 smera)
 */
@Service
public class PriceCalculatorImpl implements PriceCalculator {

    public static final Integer SUPERIOR_PP      = 100;
    private static final Integer CABIN_SUITCASE  = 100;  // 50€/smer × 2 smera
    public static final Integer INSURANCE_PP     = 12;
    public static final Integer BREAKFAST_PP     = 12;
    public static final Integer SEATS_PP         = 24;   // 12€/smer × 2 smera, po osobi
    private static final Integer EXCLUSION_PP    = 10;   // po osobi, za 2., 3. i 4. isključivanje
    public static final Integer SOLO_SURCHARGE   = 60;   // doplata za solo putnika
    public static final Integer REVEAL_BOX_FLAT  = 35;   // flat po rezervaciji

    @Override
    public PricePreviewResponse calculate(AvailableDate date, int n, AccommodationType accommodationType,
                                          int exclusionCount, int cabinSuitcaseCount,
                                          boolean hasInsurance, boolean hasBreakfast,
                                          boolean hasSeatsTogether, boolean hasRevealBox,
                                          String departureAirport) {
        int basePrice = date.getBasePrice();
        int accommodationExtra = resolveAccommodationExtra(accommodationType);
        int breakfast = hasBreakfast ? BREAKFAST_PP * date.getNumberOfNights() : 0;
        int seatsTogether = hasSeatsTogether ? SEATS_PP : 0;
        int insurance = hasInsurance ? INSURANCE_PP : 0;

        // Pravila isključivanja (koliko ih je dozvoljeno i da li je prvo gratis)
        // dolaze iz DepartureAirport - nema više if-a po kodu aerodroma.
        int exclusionCostFlat = calcExclusionCost(exclusionCount, n, departureAirport);
        int cabinSuitcaseTotal = cabinSuitcaseCount * CABIN_SUITCASE;
        int soloSurcharge = (n == 1) ? SOLO_SURCHARGE : 0;
        int revealBoxTotal = hasRevealBox ? REVEAL_BOX_FLAT : 0;

        int eurPerPerson = basePrice + accommodationExtra + breakfast + seatsTogether + insurance;
        int totalEurAll = eurPerPerson * n + cabinSuitcaseTotal + exclusionCostFlat + soloSurcharge + revealBoxTotal;

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
            .revealBoxTotal(revealBoxTotal)
            .totalEurAll(totalEurAll)
            .exclusionCount(exclusionCount)
            .numberOfTravelers(n)
            .numberOfNights(date.getNumberOfNights())
            .build();
    }

    /**
     * Cena isključivanja po pravilima konkretnog aerodroma:
     *   maxExclusions      - koliko ih je uopšte dozvoljeno (0 = nisu dostupna)
     *   firstExclusionFree - da li se prvo isključivanje ne naplaćuje
     *
     * Broj isključenja se ograničava na maxExclusions i ovde, iako
     * BookingServiceImpl.createBooking() odbija zahtev preko limita pre nego što
     * stigne dovde - da preview cene nikad ne prikaže više nego što je moguće
     * rezervisati.
     */
    private int calcExclusionCost(int exclusionCount, int n, String departureAirport) {
        DepartureAirport airport = DepartureAirport.from(departureAirport).orElse(null);
        // Nepoznat/nepostavljen aerodrom (npr. price-preview bez izbora) - najstroža
        // varijanta koja ne izmišlja popust: naplati svako isključivanje.
        if (airport == null) {
            return Math.max(0, exclusionCount) * EXCLUSION_PP * n;
        }
        if (airport.maxExclusions() == 0 || exclusionCount <= 0) return 0;

        int effective = Math.min(exclusionCount, airport.maxExclusions());
        int billable  = airport.firstExclusionFree() ? effective - 1 : effective;
        return Math.max(0, billable) * EXCLUSION_PP * n;
    }

    private int resolveAccommodationExtra(AccommodationType type) {
        if (type == null) return 0;
        return switch (type) {
            case SUPERIOR -> SUPERIOR_PP;
            default       -> 0;
        };
    }
}
