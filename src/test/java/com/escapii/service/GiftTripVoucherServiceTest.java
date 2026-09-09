package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.GiftVoucherRevealResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.PassengerInfo;
import com.escapii.repository.BookingRepository;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.impl.GiftTripVoucherServiceImpl;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Vaučer poklonjenog putovanja: kod je šifra rezervacije, /poklon stranica za
 * njega dobija termin, aerodrom i putnike - i nikad cenu - a admin može da
 * pošalje potvrdu sa vaučerom ponovo tek kad je rezervacija potvrđena.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftTripVoucherServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private VoucherPdfService voucherPdfService;
    @Mock private BookingEmailService bookingEmailService;
    @Mock private AdminBookingMapper adminBookingMapper;

    private GiftTripVoucherServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new GiftTripVoucherServiceImpl(bookingRepository, voucherPdfService, bookingEmailService, adminBookingMapper);
    }

    /** Marko plaća, putuje sa Anom; Ana je obdarena. */
    static Booking poklon(BookingStatus status) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-a3f8b2c1");
        b.setStatus(status);
        b.setIsGift(true);
        b.setGiftRecipientName("Ana Anić");
        b.setGiftRecipientEmail("ana@primer.rs");
        b.setFirstName("Marko"); b.setLastName("Marković"); b.setEmail("marko@primer.rs");
        b.setPhone("+381601234567");
        b.setDepartureAirport("BEG");
        b.setNumberOfTravelers(2);
        b.setTotalPriceAll(1000);
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.of(2026, 6, 12));
        d.setReturnDate(LocalDate.of(2026, 6, 15));
        d.setNumberOfNights(3);
        d.setDepartureAirport("BEG");
        b.setSelectedDate(d);
        b.setPassengers(new ArrayList<>(List.of(
            new PassengerInfo("Ana Anić",       "F", LocalDate.of(1992, 5, 5), null, true, "Srbija", "BB1"),
            new PassengerInfo("Marko Marković", "M", LocalDate.of(1990, 1, 1), null, true, "Srbija", "AA1"))));
        return b;
    }

    // ── kod i podaci za PDF ──────────────────────────────────────────────────

    @Test
    void kodJeSifraRezervacijeVelikimSlovima() {
        assertEquals("ESC-A3F8B2C1", TripVoucherData.code(poklon(BookingStatus.CONFIRMED)));
    }

    @Test
    void podaciZaPdf_terminAerodromPutnici_bezCene() {
        TripVoucherData d = TripVoucherData.from(poklon(BookingStatus.CONFIRMED));

        assertEquals("ESC-A3F8B2C1", d.code());
        assertEquals(LocalDate.of(2026, 6, 12), d.departureDate());
        assertEquals(LocalDate.of(2026, 6, 15), d.returnDate());
        assertEquals(3, d.nights());
        assertEquals(2, d.travelers());
        assertEquals("BEG", d.airportCode());
        assertEquals("Beograd", d.airportCity(), "grad iz DepartureAirport enuma");
        assertEquals("Aerodrom Nikola Tesla", d.airportName());
        assertEquals(List.of("Ana Anić", "Marko Marković"), d.passengers());
        assertEquals("Marko Marković", d.buyerName());

        // Strukturno: PDF podaci NEMAJU polje za cenu. Ko ga doda, mora ovde da objasni zašto.
        for (var comp : TripVoucherData.class.getRecordComponents()) {
            String n = comp.getName().toLowerCase();
            assertFalse(n.contains("price") || n.contains("cena") || n.contains("amount") || n.contains("total"),
                    "TripVoucherData nosi cenu (" + comp.getName() + ") - obdareni ne sme da je vidi");
        }
    }

    @Test
    void nepoznatAerodrom_padaNaKod() {
        Booking b = poklon(BookingStatus.CONFIRMED);
        b.setDepartureAirport("XYZ");
        TripVoucherData d = TripVoucherData.from(b);
        assertEquals("XYZ", d.airportCity());
        assertEquals("", d.airportName());
    }

    // ── /poklon stranica ─────────────────────────────────────────────────────

    @Test
    void revealPotvrdjenogPoklona_terminPutniciBezCene() {
        Booking b = poklon(BookingStatus.CONFIRMED);
        when(bookingRepository.findByBookingRefIgnoreCase("esc-a3f8b2c1")).thenReturn(Optional.of(b));

        GiftVoucherRevealResponse r = svc.reveal("  esc-a3f8b2c1 ");

        assertTrue(r.valid());
        assertEquals(GiftVoucherRevealResponse.KIND_TRIP, r.kind());
        assertNull(r.amount(),    "poklonjeno putovanje nema iznos");
        assertNull(r.expiresAt(), "poklonjeno putovanje nema rok");
        assertNull(r.giftMessage());
        assertEquals("Marko Marković", r.buyerName(), "ko poklanja");
        assertNotNull(r.trip());
        assertEquals(LocalDate.of(2026, 6, 12), r.trip().departureDate());
        assertEquals(LocalDate.of(2026, 6, 15), r.trip().returnDate());
        assertEquals(3, r.trip().nights());
        assertEquals(2, r.trip().travelers());
        assertEquals("BEG", r.trip().airportCode());
        assertEquals("Beograd", r.trip().airportCity());
        assertEquals(List.of("Ana Anić", "Marko Marković"), r.trip().passengers());
        assertEquals("Ana Anić", r.trip().recipientName());
    }

    @Test
    void revealPosleputa_iDaljeRadi() {
        when(bookingRepository.findByBookingRefIgnoreCase(anyString())).thenReturn(Optional.of(poklon(BookingStatus.COMPLETED)));
        assertTrue(svc.reveal("ESC-A3F8B2C1").valid(), "posle puta stranica je uspomena, ne rizik");
    }

    @Test
    void revealOdbija_nepotvrdjenOtkazanObicnuINepostojecu() {
        when(bookingRepository.findByBookingRefIgnoreCase("ESC-PENDING")).thenReturn(Optional.of(poklon(BookingStatus.PENDING)));
        assertFalse(svc.reveal("ESC-PENDING").valid(), "vaučer postoji tek kad uplata legne");

        when(bookingRepository.findByBookingRefIgnoreCase("ESC-CANCEL")).thenReturn(Optional.of(poklon(BookingStatus.CANCELLED)));
        assertFalse(svc.reveal("ESC-CANCEL").valid(), "otkazano nema šta da pokloni");

        Booking obicna = poklon(BookingStatus.CONFIRMED);
        obicna.setIsGift(false);
        when(bookingRepository.findByBookingRefIgnoreCase("ESC-OBICNA")).thenReturn(Optional.of(obicna));
        assertFalse(svc.reveal("ESC-OBICNA").valid(),
                "šifra obične rezervacije NE otvara podatke na /poklon - to nije poklon");

        when(bookingRepository.findByBookingRefIgnoreCase("ESC-NEMA")).thenReturn(Optional.empty());
        GiftVoucherRevealResponse nema = svc.reveal("ESC-NEMA");
        assertFalse(nema.valid());
        assertNotNull(nema.message(), "stranica prikazuje razlog");
        assertNull(nema.trip());

        assertFalse(svc.reveal(null).valid());
        assertFalse(svc.reveal("   ").valid());
        verify(bookingRepository, never()).findByBookingRefIgnoreCase(isNull());
    }

    // ── admin: pošalji ponovo ────────────────────────────────────────────────

    private HttpStatus statusOd(Runnable r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, r::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    @Test
    void resend_404_409_502() {
        when(bookingRepository.findById(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOd(() -> svc.resend(9L)));

        Booking obicna = poklon(BookingStatus.CONFIRMED);
        obicna.setIsGift(false);
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(obicna));
        assertEquals(HttpStatus.CONFLICT, statusOd(() -> svc.resend(2L)));

        when(bookingRepository.findById(3L)).thenReturn(Optional.of(poklon(BookingStatus.PENDING)));
        assertEquals(HttpStatus.CONFLICT, statusOd(() -> svc.resend(3L)),
                "pre uplate nema vaučera - ni ručno");
        verifyNoInteractions(voucherPdfService, bookingEmailService);

        when(bookingRepository.findById(4L)).thenReturn(Optional.of(poklon(BookingStatus.CONFIRMED)));
        when(voucherPdfService.generateTrip(any())).thenThrow(new RuntimeException("font fali"));
        assertEquals(HttpStatus.BAD_GATEWAY, statusOd(() -> svc.resend(4L)),
                "admin mora da vidi da PDF nije napravljen");
        verify(bookingEmailService, never()).sendBookingConfirmedNow(any(), any());

        reset(voucherPdfService);
        when(voucherPdfService.generateTrip(any())).thenReturn(new byte[]{1});
        when(bookingEmailService.sendBookingConfirmedNow(any(), any())).thenReturn(false);
        assertEquals(HttpStatus.BAD_GATEWAY, statusOd(() -> svc.resend(4L)),
                "panel mora da vidi da slanje NIJE uspelo");
    }

    @Test
    void resend_uspeh_saljePotvrduSaVaucerom() {
        Booking b = poklon(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(voucherPdfService.generateTrip(any())).thenReturn(new byte[]{1, 2, 3});
        when(bookingEmailService.sendBookingConfirmedNow(eq(b), any())).thenReturn(true);
        AdminBookingResponse odgovor = mock(AdminBookingResponse.class);
        when(adminBookingMapper.toResponse(b)).thenReturn(odgovor);

        assertSame(odgovor, svc.resend(1L));

        ArgumentCaptor<TripVoucherData> cap = ArgumentCaptor.forClass(TripVoucherData.class);
        verify(voucherPdfService).generateTrip(cap.capture());
        assertEquals("ESC-A3F8B2C1", cap.getValue().code());
        ArgumentCaptor<byte[]> pdf = ArgumentCaptor.forClass(byte[].class);
        verify(bookingEmailService).sendBookingConfirmedNow(eq(b), pdf.capture());
        assertArrayEquals(new byte[]{1, 2, 3}, pdf.getValue(), "u prilog ide bas taj PDF");
    }
}
