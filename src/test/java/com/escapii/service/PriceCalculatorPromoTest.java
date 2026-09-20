package com.escapii.service;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.service.impl.PriceCalculatorImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Promo „besplatno isključivanje": naplativa isključivanja koštaju 0, ostatak cene je isti, a u
 * odgovoru stoji koliko je kupac uštedeo. Pravila aerodroma (koliko ih je dozvoljeno) ne menja.
 */
class PriceCalculatorPromoTest {

    private final PriceCalculatorImpl calc = new PriceCalculatorImpl();

    private static AvailableDate termin() {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        d.setNumberOfNights(3);
        d.setBasePrice(300);
        return d;
    }

    private PricePreviewResponse cena(String aerodrom, int iskljucenja, int putnika, boolean promo) {
        return calc.calculate(termin(), putnika, AccommodationType.STANDARD, iskljucenja, 0,
                false, false, false, false, aerodrom, promo);
    }

    @Test
    void cetiriIskljucenjaZaDvoje_bezPromo60_saPromo0_ukupnoManjeZaTacno60() {
        PricePreviewResponse puna  = cena("BEG", 4, 2, false);
        PricePreviewResponse promo = cena("BEG", 4, 2, true);

        assertEquals(60, puna.getExclusionCostFlat(), "3 naplativa × 10 € × 2 putnika");
        assertEquals(0, promo.getExclusionCostFlat());
        assertEquals(60, promo.getExclusionPromoSavedEur());
        assertTrue(promo.getExclusionPromoApplied());
        assertEquals(puna.getTotalEurAll() - 60, promo.getTotalEurAll());
        assertEquals(puna.getEurPerPerson(), promo.getEurPerPerson(), "cena po osobi se ne menja");
        assertEquals(4, promo.getExclusionCount(), "broj isključenja ostaje zabeležen");
    }

    @Test
    void bezPromo_poljaSuPrazna() {
        PricePreviewResponse puna = cena("BEG", 4, 2, false);
        assertFalse(puna.getExclusionPromoApplied());
        assertEquals(0, puna.getExclusionPromoSavedEur());
    }

    @Test
    void jednoIskljucenjeJeIonakoBesplatno_promoNeStediNista() {
        PricePreviewResponse promo = cena("BEG", 1, 2, true);
        assertEquals(0, promo.getExclusionCostFlat());
        assertEquals(0, promo.getExclusionPromoSavedEur());
        assertEquals(cena("BEG", 1, 2, false).getTotalEurAll(), promo.getTotalEurAll());
    }

    @Test
    void aerodromBezIskljucivanja_promoNeDajeNista() {
        PricePreviewResponse promo = cena("INI", 3, 2, true);
        assertEquals(0, promo.getExclusionCostFlat());
        assertEquals(0, promo.getExclusionPromoSavedEur());
    }

    @Test
    void ustedaPratiBrojPutnika() {
        assertEquals(30, cena("BEG", 4, 1, true).getExclusionPromoSavedEur());
        assertEquals(90, cena("BEG", 4, 3, true).getExclusionPromoSavedEur());
        assertEquals(20, cena("BEG", 2, 2, true).getExclusionPromoSavedEur());
    }

    @Test
    void stariPotpisBezPromoParametra_racunaPunuCenu() {
        PricePreviewResponse stara = calc.calculate(termin(), 2, AccommodationType.STANDARD, 4, 0,
                false, false, false, false, "BEG");
        assertEquals(60, stara.getExclusionCostFlat());
        assertFalse(stara.getExclusionPromoApplied());
    }
}
