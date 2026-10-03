package com.escapii.service.email.impl;

import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.impl.TravelAddonsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Blok "Još par sitnica pre puta" u mejlu sa dokumentacijom: pojavljuje se samo kad
 * za destinaciju postoji bar jedan partnerski link, i nosi samo redove za koje link
 * postoji. Naslov mejla je bez emodžija (pravilo za sve mejlove kupcu).
 */
class ConfirmationDocumentEmailAddonsTest {

    private static final String ESIM    = "https://holafly.sjv.io/c/1/2/3?u=https%3A%2F%2Fesim.holafly.com%2Fesim-spain%2F";
    private static final String TOURS   = "https://www.getyourguide.com/sr-rs/barcelona-l45/?partner_id=TEST&a=b";
    private static final String LUGGAGE = "https://bounce.com/luggage-storage/barcelona";

    /** Naslov i HTML uhvaćeni umesto slanja. */
    private static class Uhvaceno extends EmailSender {
        String subject, html;
        Uhvaceno() { super(null); }
        @Override public boolean sendWithAttachment(String to, String subject, String html,
                                                    String n, byte[] b, String ct) {
            this.subject = subject; this.html = html; return true;
        }
    }

    private static Booking rezervacija() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-a3f8b2c1");
        b.setFirstName("Ana"); b.setEmail("ana@primer.rs");
        b.setNumberOfTravelers(2);
        b.setAssignedDestination("Barselona");
        b.setAirlineName("Wizz Air");
        b.setConfirmationDocument(new byte[]{1, 2, 3});
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(7));
        d.setReturnDate(LocalDate.now().plusDays(10));
        d.setNumberOfNights(3);
        b.setSelectedDate(d);
        return b;
    }

    private static Uhvaceno posalji(Map<String, String> linkovi) throws Exception {
        Uhvaceno u = new Uhvaceno();
        TravelAddonsService addons = mock(TravelAddonsService.class);
        when(addons.linksFor(any())).thenReturn(linkovi);
        ConfirmationDocumentEmailServiceImpl svc = new ConfirmationDocumentEmailServiceImpl(u, ime -> ime, addons);
        var polje = ConfirmationDocumentEmailServiceImpl.class.getDeclaredField("contactEmail");
        polje.setAccessible(true);
        polje.set(svc, "info@escapii.rs");
        assertTrue(svc.sendConfirmationDocument(rezervacija()), "mejl mora da ode");
        assertNotNull(u.html, "HTML nije uhvaćen");
        return u;
    }

    private static Map<String, String> svaTri() {
        // Namerno drugačiji redosled od onog u mejlu - mejl mora da ga ispravi na eSIM, ture, prtljag
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tours", TOURS);
        m.put("luggage", LUGGAGE);
        m.put("esim", ESIM);
        return m;
    }

    @Test
    void svaTriLinka_blokSaSvimRedovima_naslovBezEmodzija() throws Exception {
        Uhvaceno u = posalji(svaTri());
        String h = u.html;

        assertTrue(h.contains("Još par sitnica pre puta"), "naslov bloka");
        assertTrue(h.contains("Ništa od ovoga nije obavezno. Ali većina reši bar jednu stvar unapred - i ne požali."), "uvod");

        // URL-ovi idu kroz esc, pa & u query stringu stoji kao &amp;
        assertTrue(h.contains("href=\"" + ESIM + "\""), "eSIM link");
        assertTrue(h.contains("href=\"" + TOURS.replace("&", "&amp;") + "\""), "link za ture (escape-ovan &)");
        assertTrue(h.contains("href=\"" + LUGGAGE + "\""), "link za prtljag");

        assertTrue(h.contains("Internet od trenutka kad sletiš"), "eSIM red");
        assertTrue(h.contains("ESCAPII"), "kod za popust");
        assertTrue(h.contains("Kupuješ ga kod Holafly-ja"), "eSIM se kupuje - nije poklon");
        assertTrue(h.contains("Ulaznice i ture"), "red za ture");
        assertTrue(h.contains("Čuvanje prtljaga"), "red za prtljag");
        assertTrue(h.contains("Partnerski linkovi. Kupuješ direktno kod partnera, bez dodatnih troškova za tebe."), "napomena");

        // Redosled: eSIM, ture, prtljag - bez obzira na redosled u mapi
        int esim = h.indexOf("Internet od trenutka kad sletiš");
        int ture = h.indexOf("Ulaznice i ture");
        int prtljag = h.indexOf("Čuvanje prtljaga");
        assertTrue(esim < ture && ture < prtljag, "redosled redova");

        // Blok ide POSLE pasusa o PDF-u
        assertTrue(h.indexOf("Sačuvaj ovaj PDF") < h.indexOf("Još par sitnica pre puta"), "blok je iza pasusa o PDF-u");

        assertEquals("Zvanični podaci tvoje rezervacije · Escapii", u.subject);
        assertTrue(u.subject.chars().allMatch(c -> c < 0x2000), "naslov nosi emodži/simbol: " + u.subject);
        assertFalse(h.contains("{{"), "ostao nezamenjen token");
    }

    @Test
    void praznaMapa_nemaBloka_mejlIde() throws Exception {
        Uhvaceno u = posalji(Map.of());
        String h = u.html;

        assertFalse(h.contains("Još par sitnica"), "blok ne sme da postoji bez linkova");
        assertFalse(h.contains("Partnerski linkovi"), "ni napomena");
        assertFalse(h.contains("Pogledaj eSIM") || h.contains("Pogledaj ture") || h.contains("Pogledaj lokacije"));
        assertTrue(h.contains("Sačuvaj ovaj PDF"), "ostatak mejla je netaknut");
        assertEquals("Zvanični podaci tvoje rezervacije · Escapii", u.subject);
    }

    @Test
    void samoTure_bezEsimIPrtljagRedova() throws Exception {
        Uhvaceno u = posalji(Map.of("tours", TOURS));
        String h = u.html;

        assertTrue(h.contains("Još par sitnica pre puta"), "blok postoji");
        assertTrue(h.contains("Ulaznice i ture"), "red za ture");
        assertTrue(h.contains("Pogledaj ture"), "link za ture");

        assertFalse(h.contains("Internet od trenutka kad sletiš"), "eSIM red ne sme da postoji");
        assertFalse(h.contains("Pogledaj eSIM"));
        assertFalse(h.contains("Holafly"));
        assertFalse(h.contains("Čuvanje prtljaga"), "red za prtljag ne sme da postoji");
        assertFalse(h.contains("Pogledaj lokacije"));
    }

    /** Null umesto mape (odbrambeno) - isto kao prazna. */
    @Test
    void nullMapa_istoKaoPrazna() {
        assertEquals("", ConfirmationDocumentEmailServiceImpl.addonsBlock(null));
        assertEquals("", ConfirmationDocumentEmailServiceImpl.addonsBlock(Map.of()));
        // Nepoznat ključ se ignoriše - nema bloka samo zbog njega
        assertEquals("", ConfirmationDocumentEmailServiceImpl.addonsBlock(Map.of("nepoznato", "https://x")));
    }
}
