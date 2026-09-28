package com.escapii.service.email.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tekstovi mejla prognoze van saveta za pakovanje: pozdrav, datumi, padavine, dani bez prognoze
 * i preporuka. Svaki je ispravljen 28.09.2026 i ovde je zaključan da se greška ne vrati.
 */
class ForecastEmailTextTest {

    private static final DateTimeFormatter KRATAK = DateTimeFormatter.ofPattern("dd.MM.");

    private static String mejl(int danaDoPolaska, int noci, List<DailyForecast> prognoza) throws Exception {
        AtomicReference<String> html = new AtomicReference<>();
        EmailSender sender = new EmailSender(null) {
            @Override public boolean send(String to, String subject, String h) { html.set(h); return true; }
        };
        ForecastEmailServiceImpl svc = new ForecastEmailServiceImpl(sender);
        var polje = ForecastEmailServiceImpl.class.getDeclaredField("contactEmail");
        polje.setAccessible(true);
        polje.set(svc, "info@escapii.rs");

        AvailableDate termin = new AvailableDate();
        termin.setDepartureDate(LocalDate.now().plusDays(danaDoPolaska));
        termin.setReturnDate(LocalDate.now().plusDays(danaDoPolaska + noci));
        termin.setNumberOfNights(noci);
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test1234");
        b.setFirstName("Ana");
        b.setEmail("ana@example.com");
        b.setSelectedDate(termin);

        svc.sendForecastEmail(b, prognoza);
        return html.get();
    }

    /** Po jedan dan prognoze od danas pa {@code dana} unapred; {@code mm[i]} su padavine dana i. */
    private static List<DailyForecast> prognoza(int dana, double... mm) {
        List<DailyForecast> f = new ArrayList<>();
        for (int i = 0; i < dana; i++) {
            double p = i < mm.length ? mm[i] : 0;
            f.add(new DailyForecast(LocalDate.now().plusDays(i), p >= 1 ? 61 : 1, 22, 13, p));
        }
        return f;
    }

    @Test
    void pozdrav_zaSedamDana_sutra_danas() throws Exception {
        String sedam = mejl(7, 3, prognoza(16));
        assertTrue(sedam.contains("Tvoje putovanje je za <strong style=\"color:#2D5F6B;\">7 dana</strong> - evo šta te čeka."), "za 7 dana");

        String sutra = mejl(1, 3, prognoza(16));
        assertTrue(sutra.contains("Tvoje putovanje je <strong style=\"color:#2D5F6B;\">sutra</strong> - evo šta te čeka."), "sutra");
        assertFalse(sutra.contains("1 dan"), "ne „za 1 dan“");

        String danas = mejl(0, 3, prognoza(16));
        assertTrue(danas.contains("Tvoje putovanje je <strong style=\"color:#2D5F6B;\">danas</strong> - evo šta te čeka."), "danas");
        assertFalse(danas.contains("0 dana"), "ne „za 0 dana“");
    }

    @Test
    void preporukaKazeMejl_kaoOstaliMejlovi() throws Exception {
        String h = mejl(7, 3, prognoza(16));
        assertTrue(h.contains("Kada dobiješ mejl sa otkrićem destinacije"));
        assertFalse(h.contains("email sa otkrićem"));
    }

    /** Traka dana i lista ispod pišu datum isto: „05.10.", sa tačkom. */
    @Test
    void datumUTraciIUListiJeIstogOblika() throws Exception {
        String h = mejl(7, 3, prognoza(16));
        for (int i = 7; i <= 10; i++) {
            String datum = LocalDate.now().plusDays(i).format(KRATAK);
            assertTrue(h.contains(">" + datum + "</div>"), "traka dana nema " + datum);
            assertTrue(h.contains(" " + datum), "lista nema " + datum);
        }
    }

    /** Kapljica od 1 mm naviše, sa razmakom pred jedinicom; ispod toga je nema - isti prag kao za kišni dan. */
    @Test
    void padavine_razmakIPrag() throws Exception {
        double[] mm = new double[16];
        mm[7] = 4.2;    // polazak: kiša
        mm[8] = 0.7;    // ispod praga
        mm[9] = 1.0;    // tačno na pragu
        String h = mejl(7, 3, prognoza(16, mm));

        assertTrue(h.contains("💧&nbsp;4&nbsp;mm"), "4 mm sa razmakom");
        assertTrue(h.contains("💧&nbsp;1&nbsp;mm"), "1 mm sa razmakom");
        assertFalse(h.contains("mm</") && h.matches("(?s).*\\dmm<.*"), "broj slepljen sa jedinicom");
        assertEquals(4, h.split("💧", -1).length - 1, "dva dana sa padavinama, svaki u traci i u listi");
        assertTrue(h.contains("Kiša se očekuje 2 od 4 dana"), "savet broji ista dva dana");
    }

    /** Ručno slanje dve nedelje unapred: poslednji dani puta još nemaju prognozu. */
    @Test
    void daniBezPrognoze_istinitaNapomena() throws Exception {
        String h = mejl(13, 3, prognoza(16));          // prognoza do danas+15, povratak danas+16
        assertEquals(1, h.split("Prognoza još nije dostupna", -1).length - 1, "jedan red bez prognoze");
        assertTrue(h.contains("Za poslednji dan putovanja prognoza još nije dostupna - proveri je kad saznaš destinaciju."));

        String tri = mejl(15, 3, prognoza(16));         // pokriven samo dan polaska
        assertEquals(3, tri.split("Prognoza još nije dostupna", -1).length - 1, "tri reda bez prognoze");
        assertTrue(tri.contains("Za poslednja 3 dana putovanja prognoza još nije dostupna - proveri je kad saznaš destinaciju."));

        for (String mejl : new String[]{h, tri}) {
            assertFalse(mejl.contains("biće dostupna"), "mejl ide jednom - ne obećava se nova prognoza");
            assertFalse(mejl.contains("dostupna bliže datumu"));
            assertFalse(mejl.contains("Prognoza poslednja"));
        }
        assertFalse(mejl(7, 3, prognoza(16)).contains("još nije dostupna"), "redovno slanje pokriva ceo put");
    }

    /** Oznake koje kupac ne sme da dobije kad mejl ide redovnim putem. */
    @Test
    void krupnaKarticaJeDanPolaska() throws Exception {
        String h = mejl(7, 3, prognoza(16));
        assertTrue(h.contains("Na dan polaska"));
        assertFalse(h.contains("Trenutno vreme"));
        assertFalse(h.contains(">Danas<"));
    }
}
