package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.repository.*;
import com.escapii.service.*;
import com.escapii.service.email.BookingEmailService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kupac mora dobiti mejl tačno kad status rezervacije stvarno promeni značenje
 * za njega - potvrđeno ili otkazano - i ni u jednom drugom trenutku. Ovo je
 * regresiona brava za redosled/uslove u AdminServiceImpl.updateBookingStatus:
 * lako je slučajno pomeriti if/else granu i poslati pogrešan mejl ili nijedan.
 */
@ExtendWith(MockitoExtension.class)
class BookingStatusEmailTest {

    @Mock private AvailableDateRepository availableDateRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private TermDestinationRepository termDestinationRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private RevealEventRepository revealEventRepository;
    @Mock private CustomDateInquiryRepository inquiryRepository;
    @Mock private AdminBookingMapper adminBookingMapper;
    @Mock private DestinationMapper destinationMapper;
    @Mock private BookingEmailService bookingEmailService;
    @Mock private WaitlistService waitlistService;
    @Mock private AvailableDateService availableDateService;
    @Mock private CustomDateInquiryService inquiryService;
    @Mock private AirportLookupService airportLookupService;
    @Mock private InvoiceService invoiceService;
    @Mock private ConfirmationDocumentEmailService confirmationDocumentEmailService;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, bookingEmailService, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking booking(BookingStatus status) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test1234");
        b.setStatus(status);
        b.setNumberOfTravelers(2);
        return b;
    }

    private AvailableDate slotWith(int availableSlots) {
        AvailableDate d = new AvailableDate();
        d.setId(50L);
        d.setAvailableSlots(availableSlots);
        return d;
    }

    @Test
    void pendingNaConfirmedSaljeSamoPotvrdu() {
        Booking b = booking(BookingStatus.PENDING);
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotWith(10)));

        svc.updateBookingStatus(1L, BookingStatus.CONFIRMED);

        verify(bookingEmailService).sendBookingConfirmed(any());
        verify(bookingEmailService, never()).sendBookingCancelled(any());
    }

    @Test
    void confirmedNaCancelledSaljeSamoOtkazivanje() {
        Booking b = booking(BookingStatus.CONFIRMED);
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotWith(3)));

        svc.updateBookingStatus(1L, BookingStatus.CANCELLED);

        verify(bookingEmailService).sendBookingCancelled(any());
        verify(bookingEmailService, never()).sendBookingConfirmed(any());
    }

    /**
     * PENDING → CANCELLED (nikad nije bilo potvrđeno) ne šalje "otkazano" -
     * kupac nikad nije dobio potvrdu, pa mu "otkazano" ne bi imalo smisla.
     */
    @Test
    void pendingNaCancelledNeSaljeNista() {
        Booking b = booking(BookingStatus.PENDING);
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        // Termin se učitava kad god se status stvarno menja, čak i kad ispadne
        // da broj mesta ostaje nepromenjen (PENDING→CANCELLED ne diraslote).
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotWith(5)));

        svc.updateBookingStatus(1L, BookingStatus.CANCELLED);

        verifyNoInteractions(bookingEmailService);
    }

    @Test
    void confirmedNaCompletedNeSaljeStatusniMejl() {
        Booking b = booking(BookingStatus.CONFIRMED);
        when(bookingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(1L)).thenReturn(Optional.of(slotWith(3)));

        svc.updateBookingStatus(1L, BookingStatus.COMPLETED);

        verifyNoInteractions(bookingEmailService);
    }
}
