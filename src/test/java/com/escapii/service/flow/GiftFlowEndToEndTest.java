package com.escapii.service.flow;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.CountryDto;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.mapper.BookingMapper;
import com.escapii.model.*;
import com.escapii.repository.*;
import com.escapii.service.DestinationService;
import com.escapii.service.PriceCalculator;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.*;
import com.escapii.service.impl.*;
import com.escapii.service.weather.DailyForecast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ceo tok jedne rezervacije, kroz PRAVE servise, od pocetka do kraja:
 *
 * <pre>
 *   forma -> createBooking -> potvrda upita -> profaktura -> potvrda -> prognoza
 *         -> reveal -> reveal stranica -> grebanje -> putni dokumenti
 * </pre>
 *
 * Pravi su: BookingServiceImpl (validacija, izgradnja rezervacije), svih pet
 * mejl servisa (pravi MJML sabloni, pravi tekst), RevealServiceImpl (sta reveal
 * stranica dobija, sta grebanje okida). Lazni su: baza (mockovani repozitorijumi)
 * i SMTP - EmailSender je mock koji BELEZI svaki mejl umesto da ga posalje.
 *
 * <p>Test ispisuje trag: za svaki mejl kome ide, naslov i prvi red teksta. To je
 * odgovor na "kako izgleda flow" koji se ne moze dobiti iz jedinicnih testova, a
 * niko nije hteo da ga rucno prodje na produkciji. Nije zamena za pravu
 * rezervaciju - ne dokazuje da Resend isporuci ni da se stranica ucita - ali
 * dokazuje da kod, kad ga scheduler i admin pozovu, sastavi prave mejlove za
 * prave ljude.
 *
 * <p>Sta nije pokriveno, da se ne precita kao vise nego sto jeste: event
 * listener (AFTER_COMMIT) se ne okida bez transakcije, pa se mejlovi 1, 3 i 4
 * zovu direktno - isti metod koji bi listener pozvao. Scheduler se ne vrti;
 * prognoza i reveal se zovu kako bi ih on pozvao.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftFlowEndToEndTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private AvailableDateRepository availableDateRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private RevealEventRepository revealEventRepository;
    @Mock private PriceCalculator priceCalculator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BookingMapper bookingMapper;
    @Mock private TravelAddonsService travelAddonsService;
    @Mock private EmailSender sender;

    /** Jedan zabelezen mejl. */
    private record Poslat(String kome, String naslov, String prviRed, String prilog) {}
    private final List<Poslat> poslato = new ArrayList<>();

    private BookingServiceImpl booking;
    private BookingEmailServiceImpl bookingMail;
    private InvoiceEmailServiceImpl invoiceMail;
    private ForecastEmailServiceImpl forecastMail;
    private RevealEmailServiceImpl revealMail;
    private ConfirmationDocumentEmailServiceImpl docMail;
    private RevealServiceImpl reveal;

    // ── zica ──────────────────────────────────────────────────────────────

    private static void set(Object o, String polje, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(polje);
        f.setAccessible(true);
        f.set(o, v);
    }

    /** HTML -> prvi smisleni red teksta, da se u tragu vidi sta mejl kaze. */
    private static String prviRed(String html) {
        String t = html.replaceAll("<style[^>]*>.*?</style>", " ")
                       .replaceAll("<[^>]+>", " ")
                       .replaceAll("&nbsp;", " ")
                       .replaceAll("[ \t\r\n]+", " ")
                       .replaceAll(" ,", ",")
                       .trim();
        // preskoci preheader/sakrivene delove: uzmi deo posle prvog pojavljivanja
        // "Zdravo," ili "Vreme je" ili "Tvoje putovanje", inace pocetak
        for (String marker : new String[]{"Zdravo,", "Vreme je", "Tvoje putovanje", "Uzbu"}) {
            int i = t.indexOf(marker);
            if (i >= 0) { t = t.substring(i); break; }
        }
        return t.length() > 110 ? t.substring(0, 110) + "…" : t;
    }

    @BeforeEach
    void setUp() throws Exception {
        // SMTP koji belezi umesto da salje
        when(sender.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            poslato.add(new Poslat(inv.getArgument(0), inv.getArgument(1), prviRed(inv.getArgument(2)), null));
            return true;
        });
        when(sender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
            .thenAnswer(inv -> {
                poslato.add(new Poslat(inv.getArgument(0), inv.getArgument(1), prviRed(inv.getArgument(2)), inv.getArgument(3)));
                return true;
            });

        booking = new BookingServiceImpl(bookingRepository, availableDateRepository, destinationRepository,
                giftVoucherRepository, priceCalculator, eventPublisher, bookingMapper,
                new FinancialItemSnapshotService(), new VoucherLedger());

        // Vokativ bez mreze: menja se samo Uros, da se u tragu VIDI da obracanje
        // prolazi kroz vokativ; ostala imena u testu su ista u oba padeza.
        com.escapii.service.VocativeService vok = ime -> {
            String nn = com.escapii.service.impl.DeklinacijaVocativeService.normalize(ime);
            return nn.equals("Uroš") ? "Uroše" : nn;
        };
        bookingMail = new BookingEmailServiceImpl(sender, new DestinationService() {
            public List<Destination> getDestinationsByAirport(String a) { return List.of(); }
            public List<Destination> getAllDestinations() { return List.of(); }
            public List<CountryDto> fetchCountries() { return List.of(new CountryDto("RS", "Serbia", "Srbija")); }
        }, vok);
        set(bookingMail, "teamEmail", "tim@escapii.rs");
        set(bookingMail, "contactEmail", "info@escapii.rs");
        Method init = BookingEmailServiceImpl.class.getDeclaredMethod("initCountryNames");
        init.setAccessible(true); init.invoke(bookingMail);

        invoiceMail  = new InvoiceEmailServiceImpl(sender, vok);            set(invoiceMail,  "contactEmail", "info@escapii.rs");
        forecastMail = new ForecastEmailServiceImpl(sender);           set(forecastMail, "contactEmail", "info@escapii.rs");
        revealMail   = new RevealEmailServiceImpl(sender);             set(revealMail,   "contactEmail", "info@escapii.rs");
                                                                        set(revealMail,   "frontendUrl",  "https://escapii.rs");
        docMail      = new ConfirmationDocumentEmailServiceImpl(sender, vok); set(docMail,    "contactEmail", "info@escapii.rs");

        reveal = new RevealServiceImpl(bookingRepository, revealEventRepository, docMail, travelAddonsService);
        when(travelAddonsService.linksFor(anyString())).thenReturn(Map.of());

        // baza
        when(availableDateRepository.findById(10L)).thenReturn(Optional.of(termin()));
        when(bookingRepository.existsDuplicateBooking(anyString(), anyLong(), any())).thenReturn(false);
        when(priceCalculator.calculate(any(), anyInt(), any(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyString())).thenReturn(cena());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(1L);
            if (b.getBookingRef() == null) b.setBookingRef("ESC-e2e00001");
            return b;
        });
    }

    private AvailableDate termin() {
        AvailableDate d = new AvailableDate();
        d.setId(10L); d.setActive(true); d.setDepartureAirport("BEG");
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        d.setAvailableSlots(5); d.setBasePrice(500); d.setNumberOfNights(3);
        return d;
    }

    private PricePreviewResponse cena() {
        return PricePreviewResponse.builder()
                .basePricePerPerson(500).accommodationExtraPerPerson(0)
                .breakfastPerPerson(0).seatsTogether(0).insurancePerPerson(0)
                .eurPerPerson(500).exclusionCostFlat(0).soloSurcharge(0)
                .cabinSuitcaseCount(0).cabinSuitcaseTotal(0).revealBoxTotal(0)
                .totalEurAll(1000).exclusionCount(0).numberOfTravelers(2).numberOfNights(3)
                .build();
    }

    /** Ono sto forma posalje. Marko placa i putuje sa Anom. */
    private BookingRequest forma(boolean poklon) {
        BookingRequest r = new BookingRequest();
        r.setDepartureAirport("BEG");
        r.setNumberOfTravelers(2);
        r.setSelectedDateId(10L);
        r.setAccommodationType(AccommodationType.STANDARD);
        r.setCabinSuitcaseCount(0);
        r.setPassengers(List.of(
            new BookingRequest.PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "AA1234567"),
            new BookingRequest.PassengerInfo("Ana Anić",       "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "BB7654321")));
        r.setFirstName("Marko"); r.setLastName("Marković");
        r.setEmail("marko@primer.rs"); r.setPhone("+381601234567");
        r.setAcceptedTerms(true); r.setAcceptedPrivacy(true); r.setAcceptedGdpr(true);
        r.setConsentVersion("2026-08-24"); r.setConsentLang("sr");
        r.setFormDuration(20);
        if (poklon) {
            r.setGift(true);
            r.setGiftRecipientName("Ana Anić");      // sto padajuca lista ponudi
            r.setGiftRecipientEmail("ana@primer.rs");
        }
        return r;
    }

    // ── tok ───────────────────────────────────────────────────────────────

    /** Provede rezervaciju kroz svih devet koraka i vrati je. */
    private Booking provedi(boolean poklon) {
        poslato.clear();

        // 1. forma -> createBooking (prava validacija, prava izgradnja)
        booking.createBooking(forma(poklon));
        Booking b = captureSaved();

        // 1. potvrda upita (listener bi ovo pozvao AFTER_COMMIT)
        bookingMail.sendCustomerConfirmation(b);

        // 2. admin salje profakturu
        invoiceMail.sendInvoiceToClient(b, new byte[]{1, 2, 3}, "PF-2026-001");

        // 3. admin potvrdjuje po uplati
        b.setStatus(BookingStatus.CONFIRMED);
        bookingMail.sendBookingConfirmed(b);

        // admin unosi destinaciju i podatke agencije
        b.setAssignedDestination("Prag");
        b.setAirlineName("Air Serbia");

        // 5. scheduler: prognoza
        forecastMail.sendForecastEmail(b, List.of(
            new DailyForecast(LocalDate.now().plusDays(30), 0, 24, 14, 0.0),
            new DailyForecast(LocalDate.now().plusDays(31), 1, 22, 13, 0.2)));

        // 6. scheduler: reveal (token se generise pri slanju)
        b.setRevealToken("tok-e2e-0001");
        b.setRevealSentAt(LocalDateTime.now());
        revealMail.sendRevealEmail(b);

        // reveal stranica - sta obdareni vidi kad otvori link
        when(bookingRepository.findByRevealToken("tok-e2e-0001")).thenReturn(Optional.of(b));
        Map<String, Object> stranica = reveal.getRevealInfo("tok-e2e-0001");
        assertEquals("Prag", stranica.get("destination"));

        // 7. grebanje: agencija je vec uploadovala PDF, pa grebanje odmah salje dokumente
        b.setConfirmationDocument(new byte[]{9, 9, 9});
        b.setConfirmationDocumentFilename("prag-rezervacija.pdf");
        when(revealEventRepository.findByBookingRef(b.getBookingRef())).thenReturn(Optional.empty());
        reveal.confirmRevealed("tok-e2e-0001");

        ispisi(poklon ? "POKLON  (Marko placa, putuje Ana)" : "OBICNA  (Marko putuje sa Anom)", b, stranica);
        return b;
    }

    private Booking captureSaved() {
        var cap = org.mockito.ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    private void ispisi(String naslov, Booking b, Map<String, Object> stranica) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("  " + naslov + "   ref " + b.getBookingRef());
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println(String.format("  %-3s %-16s %-44s %s", "#", "KOME", "NASLOV", "PRVI RED"));
        int i = 1;
        for (Poslat p : poslato) {
            System.out.println(String.format("  %-3d %-16s %-44s %s", i++, p.kome(),
                    skrati(p.naslov(), 44), p.prviRed()));
            if (p.prilog() != null) System.out.println("      prilog: " + p.prilog());
        }
        System.out.println("  reveal stranica → destination=" + stranica.get("destination")
                + "  firstName=" + stranica.get("firstName")
                + "  totalPriceAll=" + (stranica.containsKey("totalPriceAll") ? stranica.get("totalPriceAll") : "(NEMA U ODGOVORU)"));
    }

    private static String skrati(String s, int n) { return s.length() > n ? s.substring(0, n - 1) + "…" : s; }

    private Poslat mejl(String naslovSadrzi) {
        return poslato.stream().filter(p -> p.naslov().contains(naslovSadrzi)).findFirst()
                .orElseThrow(() -> new AssertionError("nema mejla sa naslovom koji sadrzi: " + naslovSadrzi));
    }

    // ── tvrdnje ───────────────────────────────────────────────────────────

    @Test
    void obicnaRezervacija_sveIdeKupcu() {
        Booking b = provedi(false);

        assertEquals(6, poslato.size(), "sest mejlova kupcu: upit, profaktura, potvrda, prognoza, reveal, dokumenti");
        for (Poslat p : poslato) {
            assertEquals("marko@primer.rs", p.kome(), p.naslov() + " nije otisao kupcu");
        }
        assertTrue(mejl("Zvanični podaci").prviRed().startsWith("Zdravo, Marko,"));
        assertNotNull(reveal.getRevealInfo("tok-e2e-0001").get("totalPriceAll"), "kupac vidi cenu");
        assertFalse(Boolean.TRUE.equals(b.getIsGift()));
    }

    @Test
    void poklon_novacKupcuPutObdarenoj() {
        Booking b = provedi(true);

        assertTrue(Boolean.TRUE.equals(b.getIsGift()));
        assertEquals("ana@primer.rs", b.getGiftRecipientEmail(), "forma je poslala mejl obdarene i sacuvan je");

        // novac -> Marko
        for (String n : new String[]{"Upit primljen", "Profaktura", "potvrđena"}) {
            assertEquals("marko@primer.rs", mejl(n).kome(), n + " mora Marku");
        }
        // put -> Ana
        for (String n : new String[]{"prognoza", "destinacija je spremna", "Zvanični podaci"}) {
            assertEquals("ana@primer.rs", mejl(n).kome(), n + " mora Ani");
        }
        // obracanje u dokumentima: Ani, njenim imenom, samo imenom
        assertTrue(mejl("Zvanični podaci").prviRed().startsWith("Zdravo, Ana,"),
                "dokumenti pocinju sa 'Zdravo, Ana,' a ne sa kupcevim imenom: " + mejl("Zvanični podaci").prviRed());
        // Marko ne dobija nista o putu
        assertEquals(3, poslato.stream().filter(p -> p.kome().equals("marko@primer.rs")).count(),
                "kupac dobija tacno tri mejla, sva o novcu i stanju");
        assertEquals(3, poslato.stream().filter(p -> p.kome().equals("ana@primer.rs")).count(),
                "obdarena dobija tacno tri mejla, sva o putu");

        // reveal stranica: bez cene, sa Aninim imenom
        Map<String, Object> s = reveal.getRevealInfo("tok-e2e-0001");
        assertFalse(s.containsKey("totalPriceAll"), "obdarena ne sme da vidi sta je placeno");
        assertEquals("Ana", s.get("firstName"), "naslov reveal stranice glasi na obdarenu");
    }

    /** Isti tok, ali obdareni je prvi putnik (i kupac i on su muskarci) - nista ne zavisi od pola. */
    @Test
    void poklon_obdareniJePrviPutnik() {
        BookingRequest r = forma(true);
        r.setGiftRecipientName("Marko Marković");
        r.setGiftRecipientEmail("marko.putuje@primer.rs");
        r.setFirstName("Jelena"); r.setLastName("Jelić"); r.setEmail("jelena@primer.rs");
        poslato.clear();
        booking.createBooking(r);
        Booking b = captureSaved();
        b.setAssignedDestination("Prag"); b.setStatus(BookingStatus.CONFIRMED);
        b.setRevealToken("tok-2"); b.setRevealSentAt(LocalDateTime.now());
        b.setConfirmationDocument(new byte[]{1});

        bookingMail.sendCustomerConfirmation(b);
        invoiceMail.sendInvoiceToClient(b, new byte[]{1}, "PF-2026-002");
        revealMail.sendRevealEmail(b);
        docMail.sendConfirmationDocument(b);
        when(bookingRepository.findByRevealToken("tok-2")).thenReturn(Optional.of(b));
        ispisi("POKLON  (Jelena placa i NE putuje, putuje Marko - prvi putnik)", b, reveal.getRevealInfo("tok-2"));

        assertEquals("jelena@primer.rs", mejl("Upit primljen").kome());
        assertTrue(mejl("Upit primljen").prviRed().startsWith("Zdravo, Jelena,"),
                "Jelena ne putuje pa joj pol ne znamo - Zdravo radi bez pola: " + mejl("Upit primljen").prviRed());
        assertEquals("marko.putuje@primer.rs", mejl("destinacija je spremna").kome());
        assertTrue(mejl("Zvanični podaci").prviRed().startsWith("Zdravo, Marko,"));
    }
}
