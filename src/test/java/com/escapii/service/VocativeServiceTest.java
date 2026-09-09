package com.escapii.service;

import com.escapii.service.impl.DeklinacijaVocativeService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

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

    // ── varijante sa dijakritikom: sta se proba kad servis ne nadje ─────────

    @Test
    void jednaZamenaIdePrva() {
        List<String> v = DeklinacijaVocativeService.diacriticVariants("Uros");
        assertEquals(List.of("Uroš"), v, "jedan s, jedna varijanta");
    }

    @Test
    void dvaKandidataDajuPrvoPojedinacnePaZajednicku() {
        List<String> v = DeklinacijaVocativeService.diacriticVariants("Sasa");
        assertEquals(List.of("Šasa", "Saša", "Šaša"), v,
                "prvo varijante sa jednom zamenom (sleva nadesno), pa sa dve");
    }

    @Test
    void cImaDveMogucnosti() {
        List<String> v = DeklinacijaVocativeService.diacriticVariants("Cica");
        assertTrue(v.contains("Čica") && v.contains("Ćica"), v.toString());
    }

    @Test
    void djPostajeDjSaKvacicom() {
        List<String> v = DeklinacijaVocativeService.diacriticVariants("Djordje");
        assertTrue(v.contains("Đordje") && v.contains("Djorđe") && v.contains("Đorđe"), v.toString());
    }

    @Test
    void velikaSlovaSeCuvaju() {
        assertEquals(List.of("ŠASA", "SAŠA", "ŠAŠA"), DeklinacijaVocativeService.diacriticVariants("SASA"));
    }

    @Test
    void imeBezKandidataNemaVarijanti() {
        assertTrue(DeklinacijaVocativeService.diacriticVariants("Marko").isEmpty());
        assertTrue(DeklinacijaVocativeService.diacriticVariants("Ana").isEmpty());
    }

    @Test
    void brojVarijantiJeOgranicen() {
        // "Cascasc": tri c (po 2) + dva s... eksplozija se seče na MAX_VARIJANTI
        assertTrue(DeklinacijaVocativeService.diacriticVariants("Cascasc").size()
                <= DeklinacijaVocativeService.MAX_VARIJANTI);
    }

    @Test
    void pogodakNaVarijantiVracaVokativ() {
        // parseOptional razlikuje "nije nadjeno" od "isti u oba padeza" - to je ono
        // sto omogucava da se uopste zna KADA treba probati varijantu
        assertTrue(DeklinacijaVocativeService.parseOptional(
                "{\"status\":\"Not found\",\"vocative\":null}").isEmpty());
        assertEquals("Marko", DeklinacijaVocativeService.parseOptional(
                "{\"status\":\"Success\",\"vocative\":\"Marko\"}").orElseThrow(),
                "Marko->Marko je USPEH, ne promasaj - ne sme da okine varijante");
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
        // Najcesci realan unos - tastatura bez s/c/c. Servis sam ne zna "Uros";
        // varijanta "Uroš" ga nadje. Ovo je razlog sto varijante postoje.
        assertEquals("Uroše",  s.vocative("Uros"),  "bez dijakritike, preko varijante");
        assertEquals("Uroše",  s.vocative("uros"),  "malim slovima i bez dijakritike");
        assertEquals("Miloše", s.vocative("Milos"));
        assertEquals("Saša",   s.vocative("Sasa"));
        assertEquals("Milice", s.vocative("Milica"), "i zensko ime ume da se menja");
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
