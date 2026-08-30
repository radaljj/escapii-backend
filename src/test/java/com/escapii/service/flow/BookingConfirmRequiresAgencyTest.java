package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Agency;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.*;
import com.escapii.service.*;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.service.impl.AdminServiceImpl;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regresija za guard u AdminServiceImpl.updateBookingStatus: booking ne sme
 * u CONFIRMED bez agencije na terminu, jer bi bez agencije bio "nevidljiv"
 * u earnings dashboardu (grupiše se po agencyIdSnapshot). Privatni termin
 * sme ostati bez agencije, ali potvrda bookinga eksplicitno zahteva agenciju.
 */
@ExtendWith(MockitoExtension.class)
class BookingConfirmRequiresAgencyTest {

    @Mock private AgencyRepository agencyRepository;
    @Mock private AvailableDateRepository availableDateRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private TermDestinationRepository termDestinationRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private RevealEventRepository revealEventRepository;
    @Mock private CustomDateInquiryRepository inquiryRepository;
    @Mock private AdminBookingMapper adminBookingMapper;
    @Mock private DestinationMapper destinationMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WaitlistService waitlistService;
    @Mock private AvailableDateService availableDateService;
    @Mock private CustomDateInquiryService inquiryService;
    @Mock private AirportLookupService airportLookupService;
    @Mock private InvoiceService invoiceService;
    @Mock private ConfirmationDocumentEmailService confirmationDocumentEmailService;
    @Mock private ConfirmationDocumentAutoSender confirmationDocumentAutoSender;
    @Mock private com.escapii.service.AgencySettlementCalculator agencySettlementCalculator;
    @Mock private com.escapii.repository.BookingFinancialItemRepository bookingFinancialItemRepository;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                agencySettlementCalculator, bookingFinancialItemRepository);
        // lenient - guard testovi rano puknu i ne dolaze do save() poziva.
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking pendingBooking() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test0001");
        b.setStatus(BookingStatus.PENDING);
        b.setNumberOfTravelers(2);
        return b;
    }

    private AvailableDate slotNoAgency() {
        AvailableDate d = new AvailableDate();
        d.setId(50L);
        d.setAvailableSlots(5);
        // agency namerno null - ovo je scenario privatnog termina bez organizatora
        return d;
    }

    private AvailableDate slotWithAgency() {
        AvailableDate d = new AvailableDate();
        d.setId(50L);
        d.setAvailableSlots(5);
        Agency a = new Agency();
        a.setId(1L);
        a.setName("Test agencija");
        d.setAgency(a);
        return d;
    }

    /** PENDING → CONFIRMED bez agencije na terminu mora vratiti 400. */
    @Test
    void potvrdaBezAgencijeNaTerminuVracaBadRequest() {
        Booking b = pendingBooking();
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotNoAgency()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateBookingStatus(1L, BookingStatus.CONFIRMED));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("agenciju"),
                "poruka mora upozoriti admina da nedostaje agencija");
        // Status booking-a ne sme biti izmenjen - guard puca pre setStatus.
        assertEquals(BookingStatus.PENDING, b.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    /** PENDING → CONFIRMED sa agencijom na terminu prolazi normalno. */
    @Test
    void potvrdaSaAgencijomNaTerminuProlazi() {
        Booking b = pendingBooking();
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotWithAgency()));

        assertDoesNotThrow(() -> svc.updateBookingStatus(1L, BookingStatus.CONFIRMED));
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
        // Snapshot agencije mora biti postavljen - to je razlog zbog kojeg
        // guard postoji: bez snapshot-a booking je nevidljiv u earnings dashboardu.
        assertEquals(1L, b.getAgencyIdSnapshot());
        assertEquals("Test agencija", b.getAgencyNameSnapshot());
    }

    /** PENDING → CANCELLED (bez agencije) sme proći - guard je samo za CONFIRMED. */
    @Test
    void otkazivanjeBezAgencijeNaTerminuJeDozvoljeno() {
        Booking b = pendingBooking();
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotNoAgency()));

        assertDoesNotThrow(() -> svc.updateBookingStatus(1L, BookingStatus.CANCELLED));
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
    }

    /**
     * CONFIRMED → CONFIRMED (no-op poziv) prolazi čak i ako je agencija u međuvremenu
     * uklonjena sa termina - snapshot je već zabeležen u prošlosti i ostaje izvor
     * istine za earnings, pa nema razloga da se blokira.
     */
    @Test
    void ponovnaPotvrdaBookinguSaSnapshotomProlaziIBezAgencijeNaTerminu() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        b.setAgencyIdSnapshot(99L);
        b.setAgencyNameSnapshot("Ranija agencija");
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        // lenient - guard preskače proveru termina kada snapshot već postoji,
        // pa findByBookingId mox se nikad ne poziva u ovom scenariju.
        lenient().when(availableDateRepository.findByBookingId(1L))
                .thenReturn(Optional.of(slotNoAgency()));

        assertDoesNotThrow(() -> svc.updateBookingStatus(1L, BookingStatus.CONFIRMED));
        assertEquals(99L, b.getAgencyIdSnapshot());
        assertEquals("Ranija agencija", b.getAgencyNameSnapshot());
    }
}
