package com.escapii.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 23.07.2026: Open-Meteo je vratio HTTP 503, prognoza je preskočena za dve
 * rezervacije, a jedna (Hamburg, polazak istog dana) je ipak dobila reveal
 * bez prognoze. Dva uzroka - oba zaključana ovde:
 *
 *  1. Weather API bez ponavljanja: jedan tranzijentan 503 obori prognozu za
 *     ceo dan. Kod je poziv izvlačio kroz sendWithRetry.
 *  2. Reveal nije proveravao da je prognoza otišla - redosled poziva u
 *     scheduleru ne pomaže ako prognoza tiho padne. Sada čeka forecastSentAt.
 */
class ForecastBeforeRevealTest {

    private static String src(String p) throws Exception {
        return new String(Files.readAllBytes(Path.of(p)), StandardCharsets.UTF_8);
    }

    /** Weather pozivi moraju proći kroz ponavljanje - 503 je tranzijentan. */
    @Test
    void weatherPoziviImajuPonavljanje() throws Exception {
        String w = src("src/main/java/com/escapii/service/weather/WeatherServiceImpl.java");
        assertTrue(w.contains("sendWithRetry"),
                "HTTP pozivi moraju ići kroz sendWithRetry - jedan 503 ne sme da obori prognozu");
        assertEquals(3, countOccurrences(w, "sendWithRetry(request"),
                "sva tri poziva (Nominatim + Open-Meteo + MET Norway) moraju koristiti ponavljanje");
        // 4xx se NE ponavlja - trajna greška, ponavljanje bi samo trošilo vreme
        assertTrue(w.contains(">= 500"),
                "ponavljati se sme samo na 5xx, ne na 4xx");
    }

    /**
     * Ako primarni izvor (Open-Meteo) padne, mora postojati rezervni (MET Norway).
     * Ovo je ono što današnji 503 nije imao - jedan izvor je bio jedina tačka otkaza.
     */
    @Test
    void postojiRezervniIzvorPrognoze() throws Exception {
        String w = src("src/main/java/com/escapii/service/weather/WeatherServiceImpl.java");
        assertTrue(w.contains("fetchOpenMeteo") && w.contains("fetchMetNorway"),
                "moraju postojati dva izvora - primarni i rezervni");

        // Rezervni se sme zvati SAMO ako je primarni pao - tj. u catch grani
        int primarni = w.indexOf("fetchOpenMeteo(coords");
        int rezervni = w.indexOf("fetchMetNorway(coords");
        assertTrue(primarni > 0 && rezervni > primarni,
                "rezervni poziv mora biti posle primarnog (u fallback grani)");
    }

    /**
     * Nikad se ne smeju poslati OBA izvora - primarni uspeh mora odmah da vrati,
     * pre nego što se rezervni uopšte dosegne. Inače bi kupac mogao dobiti dve
     * prognoze.
     */
    @Test
    void primarniUspehPrekidaPreRezervnog() throws Exception {
        String w = src("src/main/java/com/escapii/service/weather/WeatherServiceImpl.java");
        int primarni = w.indexOf("fetchOpenMeteo(coords");
        int returnPosle = w.indexOf("return Optional.of(f)", primarni);
        int rezervni = w.indexOf("fetchMetNorway(coords");
        assertTrue(returnPosle > primarni && returnPosle < rezervni,
                "mora postojati 'return' između primarnog i rezervnog - "
                + "bez njega bi se oba izvora poslala (dupla prognoza)");
    }

    /**
     * Rezervni izvor NE sme dobiti ime grada - samo koordinate, kao i primarni.
     * Da MET Norway prima String grad, mogao bi ga proslediti dalje / procureti.
     */
    @Test
    void rezervniIzvorDobijaSamoKoordinate() throws Exception {
        String w = src("src/main/java/com/escapii/service/weather/WeatherServiceImpl.java");

        assertTrue(w.contains("fetchMetNorway(double lat, double lon)"),
                "rezervni mora primati koordinate (double, double), ne ime grada");
        assertTrue(w.contains("fetchOpenMeteo(double lat, double lon)"),
                "primarni mora primati koordinate (double, double), ne ime grada");
        // URL rezervnog koristi brojčane koordinate (%.4f), ne string (%s)
        int urlLine = w.indexOf("MET_NORWAY_URL");
        String url = w.substring(urlLine, w.indexOf(";", urlLine));
        assertTrue(url.contains("%.4f") && !url.contains("%s"),
                "MET URL sme imati samo koordinate, nikako ime grada");
    }

    /**
     * Reveal petlja mora eksplicitno da preskoči rezervaciju bez prognoze.
     * Ovo je jedina brana koja je 23.07 nedostajala.
     */
    @Test
    void automatskiRevealCekaPrognozu() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");

        int loop = s.indexOf("private List<Booking> sendReveals");
        assertTrue(loop > 0, "sendReveals nije pronađen");
        String body = s.substring(loop, s.indexOf("private List<Booking> sendForecasts", loop) > loop
                ? s.indexOf("private List<Booking> sendForecasts", loop) : s.length());

        assertTrue(body.contains("getForecastSentAt() == null"),
                "auto reveal mora da preskoči rezervaciju čija prognoza nije poslata");
    }

    /**
     * Scheduler i dalje šalje prognozu PRE reveala. Zajedno sa gornjom branom
     * ovo znači: prognoza uvek prethodi revealu, i po redosledu i po uslovu.
     */
    @Test
    void schedulerSaljePrognozuPreReveala() throws Exception {
        String s = src("src/main/java/com/escapii/config/DailyTaskScheduler.java");
        int f = s.indexOf("sendPendingForecasts()");
        int r = s.indexOf("sendPendingReveals()");
        assertTrue(f > 0 && r > 0 && f < r,
                "sendPendingForecasts() mora biti pre sendPendingReveals()");
    }

    /**
     * Ručno slanje reveala NE sme imati istu branu - admin mora moći da pošalje
     * reveal i kad prognoza zaglavi (npr. weather danima nedostupan, polazak blizu).
     */
    @Test
    void rucniRevealNijeBlokiran() throws Exception {
        String s = src("src/main/java/com/escapii/service/impl/BookingSchedulingServiceImpl.java");
        int m = s.indexOf("public Map<String, String> sendRevealForBooking");
        assertTrue(m > 0, "sendRevealForBooking nije pronađen");
        String body = s.substring(m, s.indexOf("\n    }", m));
        assertFalse(body.contains("getForecastSentAt() == null"),
                "ručno slanje mora ostati moguće i bez prognoze - to je adminov override");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { count++; i += needle.length(); }
        return count;
    }
}
