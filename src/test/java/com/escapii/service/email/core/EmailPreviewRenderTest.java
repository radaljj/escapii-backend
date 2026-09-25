package com.escapii.service.email.core;

import com.escapii.dto.CountryDto;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.Destination;
import com.escapii.model.PassengerInfo;
import com.escapii.service.AppErrorService;
import com.escapii.service.DestinationService;
import com.escapii.service.email.impl.BookingEmailServiceImpl;
import com.escapii.service.email.impl.ForecastEmailServiceImpl;
import com.escapii.service.email.impl.RevealEmailServiceImpl;
import com.escapii.service.voucher.VoucherPdfService;
import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Četiri mejla kupcu (upit primljen, rezervacija potvrđena, prognoza, otkriće) se
 * STVARNO renderuju i snimaju u {@code target/email-preview/} - HTML, tekstualna
 * verzija i naslov - jedini način da se pogledaju bez slanja. Uz to tvrdi ono što
 * spam filteri kažnjavaju, a lako se vrati greškom: emodži ili uzvičnik u naslovu,
 * mejl bez tekstualne verzije, prevelik HTML (Gmail seče iznad ~102 KB).
 */
class EmailPreviewRenderTest {

    private static final Path OUT = Path.of("target", "email-preview");

    private static void set(Object o, String polje, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(polje);
        f.setAccessible(true);
        f.set(o, v);
    }

    /** Hvata naslov i HTML umesto da šalje. */
    static class Uhvaceno extends EmailSender {
        String to, subject, html;
        Uhvaceno() { super(null); }
        @Override public boolean send(String to, String subject, String html) {
            this.to = to; this.subject = subject; this.html = html; return true;
        }
        @Override public boolean sendWithAttachment(String to, String subject, String html,
                                                    String n, byte[] b, String ct) {
            return send(to, subject, html);
        }
    }

    private static Booking rezervacija(LocalDate polazak) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-a3f8b2c1");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setCreatedAt(LocalDateTime.now());
        b.setFirstName("Ana"); b.setLastName("Anić"); b.setEmail("ana@primer.rs");
        b.setPhone("+381601234567");
        b.setDepartureAirport("BEG");
        b.setNumberOfTravelers(2);
        b.setBasePricePerPerson(500);
        b.setTotalPricePerPerson(500);
        b.setTotalPriceAll(1000);
        b.setRevealToken("test-token-1234567890");
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(polazak);
        d.setReturnDate(polazak.plusDays(3));
        d.setNumberOfNights(3);
        d.setDepartureAirport("BEG");
        b.setSelectedDate(d);
        b.setPassengers(new ArrayList<>(List.of(
            new PassengerInfo("Ana Anić",       "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "BB1"),
            new PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "AA1"))));
        return b;
    }

    private static BookingEmailServiceImpl bookingServis(EmailSender sender) throws Exception {
        BookingEmailServiceImpl svc = new BookingEmailServiceImpl(sender, new DestinationService() {
            public List<Destination> getDestinationsByAirport(String a) { return List.of(); }
            public List<Destination> getAllDestinations() { return List.of(); }
            public List<CountryDto> fetchCountries() { return List.of(new CountryDto("RS", "Serbia", "Srbija")); }
        }, ime -> ime, mock(VoucherPdfService.class));
        set(svc, "teamEmail", "tim@escapii.rs");
        set(svc, "contactEmail", "info@escapii.rs");
        set(svc, "appErrorService", mock(AppErrorService.class));
        Method init = BookingEmailServiceImpl.class.getDeclaredMethod("initCountryNames");
        init.setAccessible(true);
        init.invoke(svc);
        return svc;
    }

    /** 16 dana od danas, kao Open-Meteo; put je od T+7 tri noći. */
    private static List<DailyForecast> prognoza(LocalDate danas, int[][] dani) {
        List<DailyForecast> f = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int[] d = dani[Math.min(i, dani.length - 1)];
            f.add(new DailyForecast(danas.plusDays(i), d[0], d[1], d[2], d[3]));
        }
        return f;
    }

    private static void snimi(Map<String, Uhvaceno> mejlovi) throws Exception {
        Files.createDirectories(OUT);
        StringBuilder naslovi = new StringBuilder();
        for (var e : mejlovi.entrySet()) {
            Uhvaceno u = e.getValue();
            Files.writeString(OUT.resolve(e.getKey() + ".html"), u.html);
            Files.writeString(OUT.resolve(e.getKey() + ".txt"), EmailSender.toPlainText(u.html));
            naslovi.append(e.getKey()).append('\t').append(u.subject).append('\t').append(u.html.length()).append('\n');
        }
        Files.writeString(OUT.resolve("naslovi.tsv"), naslovi.toString());
    }

    @Test
    void cetiriMejlaKupcu_bezEmodzijaUNaslovu_saTekstom_ispodGmailGranice() throws Exception {
        LocalDate danas = LocalDate.now();
        LocalDate polazak = danas.plusDays(7);
        Map<String, Uhvaceno> mejlovi = new LinkedHashMap<>();

        // 1. upit primljen + 2. rezervacija potvrđena
        Uhvaceno u1 = new Uhvaceno();
        bookingServis(u1).sendCustomerConfirmation(rezervacija(polazak));
        mejlovi.put("upit-primljen", u1);
        Uhvaceno u2 = new Uhvaceno();
        bookingServis(u2).sendBookingConfirmedNow(rezervacija(polazak), null);
        mejlovi.put("rezervacija-potvrdjena", u2);

        // 3. prognoza - toplo sa jednim kišnim danom, i hladna varijanta sa snegom
        Uhvaceno u3 = new Uhvaceno();
        ForecastEmailServiceImpl prognozaSvc = new ForecastEmailServiceImpl(u3);
        set(prognozaSvc, "contactEmail", "info@escapii.rs");
        // {weatherCode, max, min, padavine×10}: danas..T+6 svejedno, T+7..T+10 su dani puta
        int[][] toplo = {
            {2, 23, 13, 0}, {2, 23, 13, 0}, {2, 23, 13, 0}, {2, 23, 13, 0}, {2, 23, 13, 0}, {2, 23, 13, 0}, {2, 23, 13, 0},
            {0, 26, 15, 0}, {1, 27, 16, 0}, {61, 22, 14, 42}, {2, 25, 15, 0}, {0, 26, 16, 0}
        };
        prognozaSvc.sendForecastEmail(rezervacija(polazak), sa(prognoza(danas, toplo)));
        mejlovi.put("prognoza-toplo", u3);

        Uhvaceno u4 = new Uhvaceno();
        ForecastEmailServiceImpl prognozaSvc2 = new ForecastEmailServiceImpl(u4);
        set(prognozaSvc2, "contactEmail", "info@escapii.rs");
        int[][] hladno = {
            {3, 6, 1, 0}, {3, 6, 1, 0}, {3, 6, 1, 0}, {3, 6, 1, 0}, {3, 6, 1, 0}, {3, 6, 1, 0}, {3, 6, 1, 0},
            {3, 4, -2, 0}, {71, 1, -4, 30}, {3, 3, -3, 0}, {1, 5, -1, 0}
        };
        prognozaSvc2.sendForecastEmail(rezervacija(polazak), sa(prognoza(danas, hladno)));
        mejlovi.put("prognoza-hladno", u4);

        // 4. otkriće destinacije
        Uhvaceno u5 = new Uhvaceno();
        RevealEmailServiceImpl revealSvc = new RevealEmailServiceImpl(u5);
        set(revealSvc, "frontendUrl", "https://escapii.rs");
        set(revealSvc, "contactEmail", "info@escapii.rs");
        revealSvc.sendRevealEmail(rezervacija(polazak));
        mejlovi.put("otkrice", u5);

        snimi(mejlovi);

        for (var e : mejlovi.entrySet()) {
            String ime = e.getKey(); Uhvaceno u = e.getValue();
            assertNotNull(u.html, ime + ": nije poslat");
            assertFalse(u.html.contains("{{"), ime + ": ostao nezamenjen token");
            assertTrue(u.subject.chars().allMatch(c -> c < 0x2000), ime + ": naslov nosi emodži/simbol: " + u.subject);
            assertFalse(u.subject.contains("!"), ime + ": uzvičnik u naslovu: " + u.subject);
            assertTrue(u.html.length() < 100_000, ime + ": HTML " + u.html.length() + " B - Gmail seče iznad ~102 KB");
            String tekst = EmailSender.toPlainText(u.html);
            assertTrue(tekst.length() > 300, ime + ": tekstualna verzija prekratka (" + tekst.length() + ")");
            assertFalse(tekst.contains("{") || tekst.contains("<"), ime + ": tekstualna verzija nosi HTML/CSS");
        }
    }

    /** Padavine u nizu su ×10 da stanu u int; ovde se vraćaju u mm. */
    private static List<DailyForecast> sa(List<DailyForecast> f) {
        List<DailyForecast> out = new ArrayList<>();
        for (DailyForecast d : f) out.add(new DailyForecast(d.date(), d.weatherCode(), d.maxTemp(), d.minTemp(), d.precipitation() / 10.0));
        return out;
    }
}
