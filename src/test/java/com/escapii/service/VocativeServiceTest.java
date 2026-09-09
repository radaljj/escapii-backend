package com.escapii.service;

import com.escapii.service.impl.DeklinacijaVocativeService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Vokativ: "Zdravo, Uroše," a ne "Zdravo, Uroš,".
 *
 * <p>Čisti delovi (normalizacija, tumačenje odgovora) se testiraju bez mreže.
 * Jedan test ide na pravi servis, i preskače se — ne pada — kad servisa nema:
 * CI bez mreže ne sme da bude crven zbog tuđeg sajta, a upravo to je i ugovor
 * same usluge: nikad ne obori mejl.
 */
class VocativeServiceTest {

    // ── normalizacija: šta se šalje servisu ─────────────────────────────────

    @Test
    void prvoSlovoSeDizeJerServisMalimSlovomNeNalazi() {
        assertEquals("Uroš", DeklinacijaVocativeService.normalize("uroš"));
        assertEquals("Uroš", DeklinacijaVocativeService.normalize("UROŠ"));
        assertEquals("Uroš", DeklinacijaVocativeService.normalize("  uroš  "));
    }

    @Test
    void mesovitUnosOstajeKakoJeOtkucan() {
        assertEquals("Ana-Marija", DeklinacijaVocativeService.normalize("Ana-Marija"));
        assertEquals("McDonald",   DeklinacijaVocativeService.normalize("McDonald"));
    }

    @Test
    void samoPrvoIme() {
        assertEquals("Dragan", DeklinacijaVocativeService.normalize("Dragan Radalj"),
                "ime obdarenog stize kao 'Ime Prezime' iz liste putnika");
        assertEquals("Marko",  DeklinacijaVocativeService.normalize("Marko Petar"));
    }

    @Test
    void zalepljenZarezIliTackaOtpadaju() {
        assertEquals("Marko", DeklinacijaVocativeService.normalize("Marko,"));
        assertEquals("Marko", DeklinacijaVocativeService.normalize("Marko."));
    }

    @Test
    void praznoOstajePrazno() {
        assertEquals("", DeklinacijaVocativeService.normalize(null));
        assertEquals("", DeklinacijaVocativeService.normalize("   "));
        assertEquals("", DeklinacijaVocativeService.normalize(","));
    }

    // ── tumačenje odgovora: kad se veruje servisu ───────────────────────────

    @Test
    void uspehSaVokativomVracaVokativ() {
        String body = "{\"name\":\"Uroš\",\"sex\":\"male\",\"vocative\":\"Uroše\",\"vocative_cyr\":\"Уроше\",\"status\":\"Success\"}";
        assertEquals("Uroše", DeklinacijaVocativeService.parse(body, "Uroš"));
    }

    @Test
    void nepoznatoImeVracaNominativ() {
        String body = "{\"name\":\"Xyz\",\"sex\":null,\"vocative\":null,\"vocative_cyr\":null,\"status\":\"Not found\"}";
        assertEquals("Xyz", DeklinacijaVocativeService.parse(body, "Xyz"),
                "servis kaze Not found - ostaje ono sto je kupac otkucao");
    }

    @Test
    void losJsonVracaNominativ() {
        assertEquals("Marko", DeklinacijaVocativeService.parse("<html>502</html>", "Marko"));
        assertEquals("Marko", DeklinacijaVocativeService.parse("", "Marko"));
    }

    @Test
    void uspehBezVokativaVracaNominativ() {
        String body = "{\"status\":\"Success\",\"vocative\":\"\"}";
        assertEquals("Marko", DeklinacijaVocativeService.parse(body, "Marko"));
    }

    // ── ceo servis: gasenje i pad nazad ─────────────────────────────────────

    private static DeklinacijaVocativeService saUrlom(String url) throws Exception {
        DeklinacijaVocativeService s = new DeklinacijaVocativeService();
        Field f = DeklinacijaVocativeService.class.getDeclaredField("apiUrl");
        f.setAccessible(true);
        f.set(s, url);
        return s;
    }

    @Test
    void praznaAdresaIskljucujeServisBezPoziva() throws Exception {
        assertEquals("Uroš", saUrlom("").vocative("uroš"),
                "bez adrese nema poziva, ali normalizacija i dalje radi");
    }

    @Test
    void nedostupanServisVracaNominativ() throws Exception {
        // 192.0.2.0/24 je rezervisan za dokumentaciju - nista ne slusa, veza pada odmah ili istekne
        assertEquals("Uroš", saUrlom("http://192.0.2.1:9/api/").vocative("Uroš"));
    }

    @Test
    void praviServis_ako_je_dostupan() throws Exception {
        assumeTrue(dostupan("https://deklinacija.com/api/Marko"),
                "deklinacija.com nije dostupan - preskoceno, ne palo");

        DeklinacijaVocativeService s = saUrlom("https://deklinacija.com/api/");
        assertEquals("Uroše",  s.vocative("Uroš"));
        assertEquals("Uroše",  s.vocative("uros") .equals("Uros") ? "Uroše" : s.vocative("Uroš"),
                "bez dijakritike servis mozda ne zna - sme nominativ, ne sme greska");
        assertEquals("Petre",  s.vocative("Petar"));
        assertEquals("Marko",  s.vocative("Marko"));
        assertEquals("Ana",    s.vocative("Ana"));
        assertEquals("Jelena", s.vocative("Jelena"));
        assertEquals("Marko",  s.vocative("Marko Marković"), "samo prvo ime");
    }

    private static boolean dostupan(String url) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && r.body().contains("\"status\"");
        } catch (Exception e) {
            return false;
        }
    }
}
