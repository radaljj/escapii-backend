package com.escapii.service;

import com.escapii.model.Booking;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Poklon: placa jedna osoba, putuje druga.
 *
 * <p>Kupac zadrzava novac i stanje rezervacije - potvrdu upita, fakturu, potvrdu,
 * otkazivanje. Obdareni dobija ono sto se tice puta - prognozu, otkrice
 * destinacije i putne dokumente.
 *
 * <p>Greska u ovoj podeli je jedina u aplikaciji koja se ne moze povuci: kad
 * otkrice destinacije jednom stigne poklanjaocu umesto obdarenom, iznenadjenje
 * je potroseno i nema ispravke. Zato se ovde zakljucava i ponasanje i OBLIK KODA.
 */
class GiftRecipientRoutingTest {

    private Booking rezervacija(boolean poklon, String kupac, String obdareni, String imeObdarenog) {
        Booking b = new Booking();
        b.setBookingRef("ESC-test0001");
        b.setFirstName("Marko");
        b.setEmail(kupac);
        b.setIsGift(poklon);
        b.setGiftRecipientEmail(obdareni);
        b.setGiftRecipientName(imeObdarenog);
        return b;
    }

    @Test
    void obicnaRezervacijaSaljeSveKupcu() {
        Booking b = rezervacija(false, "kupac@example.com", null, null);
        assertEquals("kupac@example.com", b.travellerEmail());
        assertEquals("Marko", b.travellerDisplayName());
    }

    @Test
    void poklonSaljePutneMejloveObdarenom() {
        Booking b = rezervacija(true, "kupac@example.com", "obdareni@example.com", "Ana Anić");
        assertEquals("obdareni@example.com", b.travellerEmail(),
                "prognoza, otkrice i dokumenti idu obdarenom");
        assertEquals("Ana Anić", b.travellerDisplayName());
    }

    /**
     * Forma validira, a V18 ima CHECK constraint - ali ako polje ipak nedostaje,
     * mejl mora nekome da ode. Tiho odbacen mejl je gori ishod od mejla koji ode
     * kupcu, koji za rezervaciju ionako zna.
     */
    @Test
    void poklonBezMejlaPadaNazadNaKupca() {
        assertEquals("kupac@example.com",
                rezervacija(true, "kupac@example.com", null, "Ana").travellerEmail());
        assertEquals("kupac@example.com",
                rezervacija(true, "kupac@example.com", "   ", "Ana").travellerEmail());
        assertEquals("Marko",
                rezervacija(true, "kupac@example.com", "o@e.com", "  ").travellerDisplayName(),
                "prazno ime ne sme da proizvede prazan pozdrav");
    }

    // ── Strukturno: oblik koda, ne samo ponasanje ────────────────────────────

    private String izvor(String putanja) throws Exception {
        return Files.readString(Path.of(putanja));
    }

    /** Uklanja komentare, da objasnjenje u tekstu ne prolazi kao kod. */
    private String bezKomentara(String s) {
        // Klase znakova umesto escape-ova: [*] i (?m)$ rade isto, a nijedan
        // backslash ne mora da prezivi put od uredjivaca do fajla.
        return s.replaceAll("(?s)/[*].*?[*]/", "").replaceAll("(?m)//.*$", "");
    }

    /**
     * Tri servisa koja nose destinaciju ka putniku ne smeju da adresiraju
     * {@code getEmail()}. Ovo je zamena za pregled koji niko nece uraditi kad se
     * za pola godine dodaje cetvrti mejl: prvi koji kopira postojeci servis
     * nasledi i ispravnu adresu.
     */
    @Test
    void putniMejloviNeAdresirajuKupcaDirektno() throws Exception {
        String[] servisi = {
            "src/main/java/com/escapii/service/email/impl/ForecastEmailServiceImpl.java",
            "src/main/java/com/escapii/service/email/impl/RevealEmailServiceImpl.java",
            "src/main/java/com/escapii/service/email/impl/ConfirmationDocumentEmailServiceImpl.java",
        };
        for (String s : servisi) {
            String kod = bezKomentara(izvor(s));
            assertFalse(kod.contains("booking.getEmail()"),
                    s + " adresira booking.getEmail(). Kod poklona to je POKLANJALAC, "
                      + "a ovaj mejl nosi destinaciju putniku. Koristi booking.travellerEmail().");
            assertTrue(kod.contains("travellerEmail()"),
                    s + " ne koristi travellerEmail() - podela poklona ga zaobilazi.");
        }
    }

    /**
     * Reveal salje TACNO JEDAN mejl, i to putniku.
     *
     * <p>Postojala je i kopija poklanjaocu - obavestenje da je reveal otisao.
     * Uklonjena je jer je kupac vec dobio potvrdu kad je uplatio, pa je resavala
     * nelagodu koja je vec resena, a zauzvrat je pravilo dobijalo izuzetak:
     * "put ide putniku, OSIM sto reveal jos kucne kupca". Izuzetak je mesto gde
     * ce neko ko za pola godine dodaje cetvrti mejl pogresiti granu.
     *
     * <p>Sada vazi bez izuzetka: <b>novac ide platiocu, put ide putniku.</b>
     */
    @Test
    void revealSaljeSamoJedanMejlIToPutniku() throws Exception {
        String kod = bezKomentara(izvor(
            "src/main/java/com/escapii/service/email/impl/RevealEmailServiceImpl.java"));
        // Brojanje bez regexa: '(' je metaznak, a escape-ovi u ovom
        // repou ne prezive put do fajla pouzdano.
        int poziva = 0;
        for (int idx = kod.indexOf("sender.send("); idx >= 0;
             idx = kod.indexOf("sender.send(", idx + 1)) poziva++;
        assertEquals(1, poziva,
                "reveal servis salje " + poziva + " mejla. Drugi primalac znaci da je "
              + "pravilo 'put ide putniku' dobilo izuzetak - vidi javadoc iznad.");
        assertFalse(kod.contains("getEmail()"),
                "reveal ne sme da adresira kupca ni u jednom pozivu");
    }

    /** Faktura mora da ostane na kupcu - obdareni ne placa i ne treba da vidi iznos. */
    @Test
    void fakturaNeSmeDaOdeObdarenom() throws Exception {
        String kod = bezKomentara(izvor(
            "src/main/java/com/escapii/service/email/impl/InvoiceEmailServiceImpl.java"));
        assertFalse(kod.contains("travellerEmail()"),
                "faktura je adresirana putniku; kod poklona to je pogresna osoba");
    }

    /** Reveal odgovor ne sme da nosi cenu kad je poklon - poklon kome vidis racun nije poklon. */
    @Test
    void revealSakrivaCenuKodPoklona() throws Exception {
        String kod = bezKomentara(izvor(
            "src/main/java/com/escapii/service/impl/RevealServiceImpl.java"));
        assertTrue(kod.contains("getIsGift()") && kod.contains("remove(\"totalPriceAll\")"),
                "RevealServiceImpl vise ne uklanja totalPriceAll kod poklona");
    }
}
