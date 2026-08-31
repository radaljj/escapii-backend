package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Agency;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.AgencyInvoiceSequenceRepository;
import com.escapii.repository.AgencyRepository;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.BookingFinancialItemRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.repository.DestinationRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.repository.RevealEventRepository;
import com.escapii.repository.TermDestinationRepository;
import com.escapii.service.AgencySettlementCalculator;
import com.escapii.service.AirportLookupService;
import com.escapii.service.AvailableDateService;
import com.escapii.service.CustomDateInquiryService;
import com.escapii.service.InvoiceService;
import com.escapii.service.WaitlistService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.service.impl.AdminServiceImpl;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fakturisana rezervacija (INVOICED/PAID) ne moze u CANCELLED bez prethodnog
 * VOID-a. Inace bi booking izasao iz aktivnog toka a faktura bi ostala u
 * knjigama kao vazeca - agencija ne bi znala da je stornirana.
 */
@ExtendWith(MockitoExtension.class)
class CancelAfterInvoiceGuardTest {

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
    @Mock private AgencySettlementCalculator agencySettlementCalculator;
    @Mock private BookingFinancialItemRepository bookingFinancialItemRepository;
    @Mock private AgencyInvoiceSequenceRepository agencyInvoiceSequenceRepository;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                agencySettlementCalculator, bookingFinancialItemRepository,
                agencyInvoiceSequenceRepository);
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking invoicedBooking(SettlementStatus settle) {
        Booking b = new Booking();
        b.setId(50L);
        b.setBookingRef("ESC-test0050");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setSettlementStatus(settle);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        b.setAgencyIdSnapshot(1L);
        AvailableDate d = new AvailableDate();
        d.setId(70L);
        Agency a = new Agency();
        a.setId(1L);
        a.setName("Ipanema");
        d.setAgency(a);
        b.setSelectedDate(d);
        return b;
    }

    @Test
    void invoiced_booking_ne_moze_direktno_u_cancelled() {
        Booking b = invoicedBooking(SettlementStatus.INVOICED);
        when(bookingRepository.findWithDetailsById(50L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateBookingStatus(50L, BookingStatus.CANCELLED));
        assertEquals(409, ex.getStatusCode().value(),
                "fakturisan booking mora prvo u VOID pre CANCEL-a");
        assertTrue(ex.getReason() != null && ex.getReason().contains("VOID"),
                "poruka mora reci sta admin treba da uradi");
    }

    @Test
    void paid_booking_ne_moze_direktno_u_cancelled() {
        Booking b = invoicedBooking(SettlementStatus.PAID);
        when(bookingRepository.findWithDetailsById(50L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateBookingStatus(50L, BookingStatus.CANCELLED));
        assertEquals(409, ex.getStatusCode().value(),
                "cak i placena faktura mora prvo u VOID (kroz INVOICED rollback)");
    }

    @Test
    void voided_booking_ne_udara_u_cancel_guard() {
        // Ako je faktura ranije bila VOIDED, otkazivanje ne treba biti blokirano
        // faktura guard-om (knjiga vise ne nosi aktivan racun). Booking moze pasti
        // kasnije u toku iz drugih razloga (nema termina, itd.) - to nije nas problem.
        Booking b = invoicedBooking(SettlementStatus.VOIDED);
        b.setAgencyVoidedAt(java.time.LocalDateTime.now());
        b.setAgencyVoidReason("test");
        when(bookingRepository.findWithDetailsById(50L)).thenReturn(Optional.of(b));

        try {
            svc.updateBookingStatus(50L, BookingStatus.CANCELLED);
        } catch (ResponseStatusException e) {
            assertFalse(e.getReason() != null && e.getReason().contains("VOID"),
                    "VOIDED booking ne sme biti odbijen faktura-guardom");
        } catch (Exception ignoredOtherFailure) {
            // Ok - test provera je da NIJE bilo odbijeno zbog fakture, a to je
            // dokazano tako sto smo dosli do drugog problema (NPE u toku dalje)
        }
    }
}
