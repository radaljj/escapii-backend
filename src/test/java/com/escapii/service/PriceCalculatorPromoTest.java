package com.escapii.service;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;
import com.escapii.service.impl.PriceCalculatorImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Promo „besplatna isključivanja": kod kaže koliko isključivanja UKUPNO ne košta ništa. SKIP3 = tri
 * (prvo je besplatno i bez koda, pa kod stvarno poklanja drugo i treće), a četvrto se naplaćuje
 * kao i do sada. Ostatak cene je isti, u odgovoru stoji ušteda, a pravila aerodroma ostaju.
 */
class PriceCalculatorPromoTest {

    private static final int SKIP3 = 3;

    private final PriceCalculatorImpl calc = new PriceCalculatorImpl();

    private static AvailableDate termin() {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        d.setNumberOfNights(3);
        d.setBasePrice(300);
        return d;
    }

    private PricePreviewResponse cena(String aerodrom, int iskljucenja, int putnika, int promoBesplatnih) {
        return calc.calculate(termin(), putnika, AccommodationType.STANDARD, iskljucenja, 0,
                false, false, false, false, aerodrom, promoBesplatnih);
    }

    @Test
    void skip3_cetiriIskljucenjaZaDvoje_naplacujeSeSamoCetvrto() {
        PricePreviewResponse puna  = cena("BEG", 4, 2, 0);
        PricePreviewResponse promo = cena("BEG", 4, 2, SKIP3);

        assertEquals(60, puna.getExclusionCostFlat(), "bez koda: 3 naplativa × 10 € × 2 putnika");
        assertEquals(20, promo.getExclusionCostFlat(), "uz SKIP3: samo četvrto, 10 € × 2 putnika");
        assertEquals(40, promo.getExclusionPromoSavedEur(), "drugo i treće su poklonjeni");
        assertTrue(promo.getExclusionPromoApplied());
        assertEquals(3, promo.getExclusionPromoFreeCount());
        assertEquals(puna.getTotalEurAll() - 40, promo.getTotalEurAll());
        assertEquals(puna.getEurPerPerson(), promo.getEurPerPerson(), "cena po osobi se ne menja");
        assertEquals(4, promo.getExclusionCount(), "broj isključenja ostaje zabeležen");
    }

    @Test
    void skip3_doTriIskljucenja_nistaSeNeNaplacuje() {
        assertEquals(0,  cena("BEG", 3, 2, SKIP3).getExclusionCostFlat());
        assertEquals(40, cena("BEG", 3, 2, SKIP3).getExclusionPromoSavedEur());
        assertEquals(0,  cena("BEG", 2, 2, SKIP3).getExclusionCostFlat());
        assertEquals(20, cena("BEG", 2, 2, SKIP3).getExclusionPromoSavedEur());
    }

    @Test
    void promoSeNeSabiraSaPrvimGratis_triBesplatnaUkupno_neCetiri() {
        // da se sabira, uz SKIP3 bi bila besplatna sva četiri
        assertEquals(10, cena("BEG", 4, 1, SKIP3).getExclusionCostFlat());
    }

    @Test
    void brojBesplatnihJePodesiv_dvaIliSvaCetiri() {
        assertEquals(40, cena("BEG", 4, 2, 2).getExclusionCostFlat(), "promo na 2: naplaćuju se treće i četvrto");
        assertEquals(20, cena("BEG", 4, 2, 2).getExclusionPromoSavedEur());
        assertEquals(0,  cena("BEG", 4, 2, 4).getExclusionCostFlat(), "promo na 4: sve besplatno");
        assertEquals(60, cena("BEG", 4, 2, 4).getExclusionPromoSavedEur());
    }

    @Test
    void bezPromo_poljaSuPrazna() {
        PricePreviewResponse puna = cena("BEG", 4, 2, 0);
        assertFalse(puna.getExclusionPromoApplied());
        assertEquals(0, puna.getExclusionPromoSavedEur());
        assertEquals(0, puna.getExclusionPromoFreeCount());
    }

    @Test
    void jednoIskljucenjeJeIonakoBesplatno_promoNeStediNista() {
        PricePreviewResponse promo = cena("BEG", 1, 2, SKIP3);
        assertEquals(0, promo.getExclusionCostFlat());
        assertEquals(0, promo.getExclusionPromoSavedEur());
        assertEquals(cena("BEG", 1, 2, 0).getTotalEurAll(), promo.getTotalEurAll());
    }

    @Test
    void aerodromBezIskljucivanja_promoNeDajeNista() {
        PricePreviewResponse promo = cena("INI", 3, 2, SKIP3);
        assertEquals(0, promo.getExclusionCostFlat());
        assertEquals(0, promo.getExclusionPromoSavedEur());
    }

    @Test
    void ustedaPratiBrojPutnika() {
        assertEquals(20, cena("BEG", 4, 1, SKIP3).getExclusionPromoSavedEur());
        assertEquals(60, cena("BEG", 4, 3, SKIP3).getExclusionPromoSavedEur());
        assertEquals(30, cena("BEG", 4, 3, SKIP3).getExclusionCostFlat());
    }

    @Test
    void stariPotpisBezPromoParametra_racunaPunuCenu() {
        PricePreviewResponse stara = calc.calculate(termin(), 2, AccommodationType.STANDARD, 4, 0,
                false, false, false, false, "BEG");
        assertEquals(60, stara.getExclusionCostFlat());
        assertFalse(stara.getExclusionPromoApplied());
    }
}
