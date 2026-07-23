package com.escapii.service.weather;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.ForecastEmailServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zove UŽIVO oba weather izvora (Open-Meteo + MET Norway rezervni) za iste
 * koordinate, generiše forecast mejl iz svakog, i dokazuje dve stvari:
 *
 *   1. Nijedan mejl ne sadrži ime grada - ni primarni ni rezervni. Rezervni
 *      izvor dobija samo koordinate, kao i primarni.
 *   2. Oba mejla imaju identičnu strukturu - kupac ne može da primeti koji
 *      je izvor korišćen.
 *
 * Traži internet (zove prave API-je). Ako mreža nije dostupna, test se
 * preskače umesto da lažno padne.
 */
class ForecastEmailParityTest {

    // Hamburg - koordinate, NE ime. Ovako i produkcija zove weather API.
    private static final double LAT = 53.5511;
    private static final double LON = 9.9937;
    private static final String CITY = "Hamburg"; // samo za proveru da ga NEMA u mejlu

    @Test
    @SuppressWarnings("unchecked")
    void obaIzvoraDajuIstiMejlBezImenaGrada() throws Exception {
        var ws = new WeatherServiceImpl();
        Method open = WeatherServiceImpl.class.getDeclaredMethod("fetchOpenMeteo", double.class, double.class);
        Method met  = WeatherServiceImpl.class.getDeclaredMethod("fetchMetNorway", double.class, double.class);
        open.setAccessible(true);
        met.setAccessible(true);

        List<DailyForecast> openMeteo, metNorway;
        try {
            openMeteo = (List<DailyForecast>) open.invoke(ws, LAT, LON);
            metNorway = (List<DailyForecast>) met.invoke(ws, LAT, LON);
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.abort("Mreža nedostupna, preskačem: " + e.getMessage());
            return;
        }

        assertFalse(openMeteo.isEmpty(), "Open-Meteo nije vratio podatke");
        assertFalse(metNorway.isEmpty(), "MET Norway nije vratio podatke");

        var cap = new AtomicReference<String>();
        EmailSender sender = new EmailSender(null) {
            @Override public boolean send(String to, String s, String html) { cap.set(html); return true; }
        };
        var fes = new ForecastEmailServiceImpl(sender);
        var f = ForecastEmailServiceImpl.class.getDeclaredField("contactEmail");
        f.setAccessible(true);
        f.set(fes, "info@escapii.rs");

        Booking booking = booking();

        fes.sendForecastEmail(booking, openMeteo);
        String openHtml = cap.get();

        fes.sendForecastEmail(booking, metNorway);
        String metHtml = cap.get();

        // Sačuvaj oba za vizuelnu proveru
        Path dir = Path.of(System.getProperty("java.io.tmpdir"));
        Files.writeString(dir.resolve("mail-forecast-openmeteo.html"), openHtml);
        Files.writeString(dir.resolve("mail-forecast-metnorway.html"), metHtml);
        System.out.println("PREVIEW_DIR=" + dir);
        System.out.println("Open-Meteo dana: " + openMeteo.size() + " | MET Norway dana: " + metNorway.size());

        // ── 1. NIJEDAN mejl ne sme sadržati ime grada ──
        assertFalse(openHtml.contains(CITY), "Open-Meteo mejl sadrži ime grada!");
        assertFalse(metHtml.contains(CITY),  "MET Norway mejl sadrži ime grada!");

        // ── 2. Ista struktura - isti ključni markeri u oba ──
        for (String marker : List.of(
                "Tvoja vremenska prognoza",   // naslov
                "Trenutno vreme",             // hero
                "POLAZAK",                    // datum polaska
                "Preporuka")) {               // napomena
            assertTrue(openHtml.contains(marker), "Open-Meteo mejl nema: " + marker);
            assertTrue(metHtml.contains(marker),  "MET Norway mejl nema: " + marker);
        }
    }

    private Booking booking() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test1234");
        b.setFirstName("Marko");
        b.setEmail("marko@example.com");
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(5));
        d.setReturnDate(LocalDate.now().plusDays(8));
        d.setNumberOfNights(3);
        b.setSelectedDate(d);
        return b;
    }
}
