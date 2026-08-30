package com.escapii.model;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.service.impl.PriceCalculatorImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aerodromi polaska su definisani na jednom mestu (DepartureAirport) - kartice na
 * sajtu, dropdown u panelu, validacija i cena isključivanja čitaju odatle.
 *
 * Ovi testovi drže to pravilo: dodavanje aerodroma sme biti samo nov red u enumu,
 * a pravila (koliko isključenja, da li je prvo gratis) moraju stvarno da utiču na
 * cenu - inače bi neko lako "dodao" aerodrom koji se tiho ponaša kao BEG.
 */
class DepartureAirportRulesTest {

    private final PriceCalculatorImpl calc = new PriceCalculatorImpl();

    private AvailableDate date() {
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        d.setNumberOfNights(3);
        d.setBasePrice(300);
        return d;
    }

    private int exclusionCost(String airport, int exclusions, int travelers) {
        PricePreviewResponse p = calc.calculate(date(), travelers, AccommodationType.STANDARD,
                exclusions, 0, false, false, false, false, airport);
        return p.getExclusionCostFlat();
    }

    // ── Prepoznavanje koda ───────────────────────────────────────────────────

    @Test
    void kodSePrepoznajeBezObziraNaVelicinuSlovaIRazmake() {
        assertTrue(DepartureAirport.from(" beg ").isPresent());
        assertEquals(DepartureAirport.BEG, DepartureAirport.from("BeG").orElseThrow());
        assertTrue(DepartureAirport.isValid("BUD"));
    }

    @Test
    void nepostojeciKodNijeValidan() {
        assertFalse(DepartureAirport.isValid("XXX"));
        assertFalse(DepartureAirport.isValid(null));
        assertFalse(DepartureAirport.isValid(""));
        // ZAG/TIM su bili u staroj regex validaciji ali nikad nisu bili u ponudi -
        // sada se ispravno odbijaju dok se stvarno ne dodaju u enum.
        assertFalse(DepartureAirport.isValid("ZAG"));
    }

    @Test
    void nazivGradaSeIzvlaciIzDefinicije() {
        assertEquals("Budimpešta", DepartureAirport.cityNameOf("BUD"));
        assertEquals("Beograd",    DepartureAirport.cityNameOf("beg"));
        // Nepoznat kod vraća sam kod - stari zapisi u bazi ne smeju da sruše prikaz
        assertEquals("XXX", DepartureAirport.cityNameOf("XXX"));
    }

    // ── Pravila isključivanja kroz cenu ──────────────────────────────────────

    @Test
    void budImaIstaPravilaKaoBeg() {
        assertEquals(DepartureAirport.BEG.maxExclusions(), DepartureAirport.BUD.maxExclusions());
        assertEquals(DepartureAirport.BEG.firstExclusionFree(), DepartureAirport.BUD.firstExclusionFree());
        // i stvarno istu cenu, ne samo iste vrednosti u enumu
        for (int n = 0; n <= 4; n++) {
            assertEquals(exclusionCost("BEG", n, 2), exclusionCost("BUD", n, 2),
                    "BUD i BEG moraju imati istu cenu za " + n + " isključenja");
        }
    }

    @Test
    void prvoIskljucenjeJeGratisTamoGdeJeTakoDefinisano() {
        assertEquals(0, exclusionCost("BEG", 1, 2), "prvo isključenje je besplatno");
        assertEquals(0, exclusionCost("BUD", 1, 2), "prvo isključenje je besplatno");
        // 2 isključenja = jedno naplaćeno × 10€ × 2 putnika
        assertEquals(20, exclusionCost("BEG", 2, 2));
        // Regresija: 4 isključenja = 3 naplaćena × 10€ × 2 putnika
        assertEquals(60, exclusionCost("BEG", 4, 2));
    }

    @Test
    void iniNeNaplacujeIskljucenjaJerIhNeDozvoljava() {
        assertEquals(0, DepartureAirport.INI.maxExclusions());
        assertFalse(DepartureAirport.INI.allowsExclusions());
        assertEquals(0, exclusionCost("INI", 3, 2), "INI ne sme naplatiti isključenja");
    }

    @Test
    void cenaSeNeRacunaPrekoDozvoljenogMaksimuma() {
        // 4 je maksimum za BEG - 6 poslatih ne sme koštati više od 4
        assertEquals(exclusionCost("BEG", 4, 2), exclusionCost("BEG", 6, 2));
    }

    @Test
    void nepoznatAerodromNeDobijaBesplatnoIskljucenje() {
        // price-preview može stići bez aerodroma; tada se ne izmišlja popust
        assertEquals(20, exclusionCost(null, 1, 2));
    }
}
