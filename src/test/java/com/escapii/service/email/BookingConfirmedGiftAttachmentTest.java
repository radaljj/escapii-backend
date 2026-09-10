package com.escapii.service.email;

import com.escapii.dto.CountryDto;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.Destination;
import com.escapii.model.PassengerInfo;
import com.escapii.service.AppErrorService;
import com.escapii.service.DestinationService;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.BookingEmailServiceImpl;
import com.escapii.service.voucher.TripVoucherData;
import com.escapii.service.voucher.VoucherPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Potvrda rezervacije kad je putovanje poklon: kupac uz nju dobija PDF vaučer
 * u prilogu i rečenicu o njemu ispod uvodnog pasusa. Kad nije poklon - ništa
 * od toga. Kad PDF pukne - potvrda ipak ide, bez priloga, a greška se beleži.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingConfirmedGiftAttachmentTest {

    @Mock private EmailSender sender;
    @Mock private VoucherPdfService voucherPdfService;
    @Mock private AppErrorService appErrorService;

    private BookingEmailServiceImpl svc;

    private static void set(Object o, String polje, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(polje);
        f.setAccessible(true);
        f.set(o, v);
    }

    @BeforeEach
    void setUp() throws Exception {
        svc = new BookingEmailServiceImpl(sender, new DestinationService() {
            public List<Destination> getDestinationsByAirport(String a) { return List.of(); }
            public List<Destination> getAllDestinations() { return List.of(); }
            public List<CountryDto> fetchCountries() { return List.of(new CountryDto("RS", "Serbia", "Srbija")); }
        }, ime -> ime, voucherPdfService);
        set(svc, "teamEmail", "tim@escapii.rs");
        set(svc, "contactEmail", "info@escapii.rs");
        set(svc, "appErrorService", appErrorService);
        Method init = BookingEmailServiceImpl.class.getDeclaredMethod("initCountryNames");
        init.setAccessible(true);
        init.invoke(svc);

        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);
        when(sender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(true);
    }

    /** Marko plaća, putuje sa Anom; kad je poklon, Ana je obdarena. */
    private static Booking rezervacija(boolean poklon) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-a3f8b2c1");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setCreatedAt(LocalDateTime.now());
        b.setFirstName("Marko"); b.setLastName("Marković"); b.setEmail("marko@primer.rs");
        b.setPhone("+381601234567");
        b.setDepartureAirport("BEG");
        b.setNumberOfTravelers(2);
        b.setTotalPriceAll(1000);
        b.setTotalPricePerPerson(500);
        b.setBasePricePerPerson(500);
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.of(2026, 6, 12));
        d.setReturnDate(LocalDate.of(2026, 6, 15));
        d.setNumberOfNights(3);
        d.setDepartureAirport("BEG");
        b.setSelectedDate(d);
        b.setPassengers(new ArrayList<>(List.of(
            new PassengerInfo("Ana Anić",       "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "BB1"),
            new PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "AA1"))));
        if (poklon) {
            b.setIsGift(true);
            b.setGiftRecipientName("Ana Anić");
            b.setGiftRecipientEmail("ana@primer.rs");
            b.setGiftMessage("Srećan rođendan!");
        }
        return b;
    }

    @Test
    void poklon_potvrdaNosiVaucerUPrilogu_iRecenicuONjemu() {
        Booking b = rezervacija(true);
        when(voucherPdfService.generateTrip(any())).thenReturn(new byte[]{1, 2, 3});

        svc.sendBookingConfirmed(b);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> pdf  = ArgumentCaptor.forClass(byte[].class);
        verify(sender).sendWithAttachment(eq("marko@primer.rs"), eq("Rezervacija potvrđena - ESC-a3f8b2c1"),
                html.capture(), eq("escapii-poklon-putovanje-ESC-A3F8B2C1.pdf"), pdf.capture(), eq("application/pdf"));
        verify(sender, never()).send(anyString(), anyString(), anyString());
        assertArrayEquals(new byte[]{1, 2, 3}, pdf.getValue());

        String h = html.getValue();
        assertTrue(h.contains("iščekuj avanturu."), "uvodni pasus, ispravljen pravopis");
        assertTrue(h.contains("U prilogu ti šaljemo"), "rečenica o prilogu ide odmah ispod uvoda");
        assertTrue(h.contains("PDF vaučer za poklonjeno putovanje"), h.substring(h.indexOf("Zdravo")));
        assertTrue(h.contains("odštampaš ili proslediš osobi kojoj ga poklanjaš"));
        assertTrue(h.contains("Na vaučeru nema cene"));
        assertTrue(h.contains("stižu na <strong style=\"color:#1E2D2F;\">ana@primer.rs</strong>"),
                "kupcu se kaže kome idu mejlovi o putu");
        assertTrue(h.indexOf("iščekuj avanturu.") < h.indexOf("U prilogu ti šaljemo"), "rečenica je ISPOD tog pasusa");
        // vremenska linija: kupac ne dobija prognozu ni otkrice - obdarena osoba dobija
        assertTrue(h.contains("Obdarena osoba dobija prognozu"), "prognoza ide obdarenoj osobi");
        assertTrue(h.contains("obdarena osoba saznaje gde ide"), "otkrice ide obdarenoj osobi");
        assertFalse(h.contains("stižu na tvoj email"), "kupcu se ne sme reci da destinacija stize NJEMU");
        assertFalse(h.contains("spakovač"), "ispravljen pravopis");
        assertFalse(h.contains("{{"), "zaostao placeholder");

        // PDF se pravi iz iste rezervacije - šifra velikim slovima, putnici, bez cene
        ArgumentCaptor<TripVoucherData> data = ArgumentCaptor.forClass(TripVoucherData.class);
        verify(voucherPdfService).generateTrip(data.capture());
        assertEquals("ESC-A3F8B2C1", data.getValue().code());
        assertEquals(List.of("Ana Anić", "Marko Marković"), data.getValue().passengers());
        assertEquals("Srećan rođendan!", data.getValue().giftMessage(), "poruka kupca ide na PDF");
        verifyNoInteractions(appErrorService);
    }

    @Test
    void obicnaRezervacija_potvrdaBezPrilogaIBezRecenice() {
        Booking b = rezervacija(false);

        svc.sendBookingConfirmed(b);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("marko@primer.rs"), eq("Rezervacija potvrđena - ESC-a3f8b2c1"), html.capture());
        verify(sender, never()).sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        verifyNoInteractions(voucherPdfService);

        String h = html.getValue();
        assertTrue(h.contains("iščekuj avanturu.</div>"), "uvod se završava tu, bez nastavka");
        assertTrue(h.contains("šta da spakuješ"), "ispravljen pravopis, obracanje kupcu koji putuje");
        assertTrue(h.contains("Detalji putovanja stižu na tvoj email"), "bez poklona kupac i putuje");
        assertFalse(h.contains("bdarena osoba"));
        assertFalse(h.contains("🎁"));
        assertFalse(h.contains("U prilogu"));
        assertFalse(h.contains("vaučer za poklonjeno"));
        assertFalse(h.contains("{{"));
    }

    @Test
    void pdfPukne_potvrdaIpakIde_bezPriloga_saDrugomRecenicom_gresakaZabelezena() {
        Booking b = rezervacija(true);
        when(voucherPdfService.generateTrip(any())).thenThrow(new RuntimeException("font fali"));

        svc.sendBookingConfirmed(b);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("marko@primer.rs"), anyString(), html.capture());
        verify(sender, never()).sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        String h = html.getValue();
        assertFalse(h.contains("U prilogu ti šaljemo"), "ne sme da obeća prilog koji ne postoji");
        assertTrue(h.contains("stiže ti u posebnom mejlu"), "kaže da vaučer stiže naknadno");
        assertTrue(h.contains("ana@primer.rs"));
        verify(appErrorService).record(eq("PDF gift-trip-voucher"), eq(0), any());
    }

    @Test
    void sinhronaVarijanta_vracaIshodSlanja() {
        Booking b = rezervacija(true);
        when(sender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(false);

        assertFalse(svc.sendBookingConfirmedNow(b, new byte[]{9}), "admin mora da vidi da nije poslato");
        verify(appErrorService).record(eq("EMAIL booking-confirmed"), eq(0), any());
        verifyNoInteractions(voucherPdfService);

        when(sender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(true);
        assertTrue(svc.sendBookingConfirmedNow(b, new byte[]{9}));
    }
}
