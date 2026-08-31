package com.escapii.service.flow;

import com.escapii.dto.AgencySettlementResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AgencyInvoiceSequence;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Finalize je "point of no return" - jednom pusten broj fakture zakljucava
 * troskove, sledeci put ne moze dvaput. Storno mora ostati vidljiv u revizji
 * (broj se ne recikla).
 */
@ExtendWith(MockitoExtension.class)
class AgencyInvoiceFinalizeTest {

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

    private Booking confirmedBooking(SettlementStatus status) {
        Booking b = new Booking();
        b.setId(42L);
        b.setBookingRef("ESC-test0042");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setSettlementStatus(status);
        b.setAgencyNameSnapshot("Ipanema Travel");
        b.setFinancialItems(new ArrayList<>());
        return b;
    }

    private AgencySettlementResponse readyPreview() {
        return AgencySettlementResponse.builder()
                .readyForInvoice(true)
                .netSettlement(new BigDecimal("124.50"))
                .validationErrors(List.of())
                .build();
    }

    @Test
    void finalize_koristi_pesimisticki_lock_protiv_paralelnih_klikova() {
        // Regresija: bez findByIdForUpdate dva paralelna finalize mogla su proci
        // ready checkom i uzeti dva broja iz sekvence. Ovaj test verifikuje da
        // sam poziv uopste ide kroz lock varijantu upita.
        Booking b = confirmedBooking(SettlementStatus.READY_FOR_INVOICE);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(readyPreview());
        AgencyInvoiceSequence seq = new AgencyInvoiceSequence(java.time.LocalDate.now().getYear());
        seq.setLastSeq(0);
        when(agencyInvoiceSequenceRepository.findByYear(anyInt())).thenReturn(Optional.of(seq));
        when(agencyInvoiceSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.finalizeAgencyInvoice(42L);

        verify(bookingRepository).findByIdForUpdate(42L);
        verify(bookingRepository, never()).findById(42L);
    }

    @Test
    void finalize_uspesan_kreiraInvoiceNumberIPrelazNaInvoiced() {
        Booking b = confirmedBooking(SettlementStatus.READY_FOR_INVOICE);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(readyPreview());
        AgencyInvoiceSequence seq = new AgencyInvoiceSequence(java.time.LocalDate.now().getYear());
        seq.setLastSeq(0);
        when(agencyInvoiceSequenceRepository.findByYear(anyInt())).thenReturn(Optional.of(seq));
        when(agencyInvoiceSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.finalizeAgencyInvoice(42L);

        assertEquals(SettlementStatus.INVOICED, b.getSettlementStatus());
        assertNotNull(b.getAgencyInvoiceNumber());
        assertTrue(b.getAgencyInvoiceNumber().startsWith("ESC-AG-"),
                "invoice broj mora imati agencijski prefiks");
        assertNotNull(b.getAgencyInvoicedAt());
    }

    @Test
    void finalize_ne_moze_dvaput() {
        Booking b = confirmedBooking(SettlementStatus.INVOICED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0001");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void finalize_blokiran_kad_nije_readyForInvoice() {
        Booking b = confirmedBooking(SettlementStatus.NEEDS_COSTS);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(
                AgencySettlementResponse.builder()
                        .readyForInvoice(false)
                        .validationErrors(List.of("Nedostaju troskovi agencije"))
                        .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void finalize_odbijen_kad_escapii_placa_agenciji() {
        // Vaucer je vecі od Escapii zarade - klasicna faktura Escapii→agencija
        // nema smisla, agencija treba da posalje SVOJU fakturu Escapii-ju.
        Booking b = confirmedBooking(SettlementStatus.READY_FOR_INVOICE);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(
                com.escapii.dto.AgencySettlementResponse.builder()
                        .readyForInvoice(true)
                        .netSettlement(new BigDecimal("-130.50"))
                        .whoPaysWhom(com.escapii.dto.AgencySettlementResponse.WhoPaysWhom.ESCAPII_PAYS_AGENCY)
                        .validationErrors(List.of())
                        .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
        assertEquals(422, ex.getStatusCode().value(),
                "ESCAPII_PAYS_AGENCY nije za obicnu fakturu");
    }

    @Test
    void finalize_odbijen_ako_je_ranije_bio_VOIDED() {
        Booking b = confirmedBooking(SettlementStatus.VOIDED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void finalize_odbijen_za_pending_booking() {
        Booking b = confirmedBooking(SettlementStatus.READY_FOR_INVOICE);
        b.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
        assertEquals(409, ex.getStatusCode().value(),
                "PENDING = kupac jos nije platio, ne fakturisemo agenciji");
    }

    @Test
    void finalize_odbijen_za_cancelled_booking() {
        Booking b = confirmedBooking(SettlementStatus.READY_FOR_INVOICE);
        b.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class,
                () -> svc.finalizeAgencyInvoice(42L));
    }

    @Test
    void statusPrelaz_invoiced_paid_setujePaidAt() {
        Booking b = confirmedBooking(SettlementStatus.INVOICED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0001");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(readyPreview());

        svc.updateSettlementStatus(42L, SettlementStatus.PAID);

        assertEquals(SettlementStatus.PAID, b.getSettlementStatus());
        assertNotNull(b.getAgencyPaidAt());
        assertEquals("ESC-AG-2026-0001", b.getAgencyInvoiceNumber(), "broj fakture ostaje");
    }

    @Test
    void storno_INVOICED_u_VOIDED_cuva_broj_fakture() {
        Booking b = confirmedBooking(SettlementStatus.INVOICED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        b.setAgencyInvoicedAt(java.time.LocalDateTime.now());
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(readyPreview());

        svc.voidAgencyInvoice(42L, "duplirano - originalna faktura je 0007");

        assertEquals(SettlementStatus.VOIDED, b.getSettlementStatus());
        assertEquals("ESC-AG-2026-0007", b.getAgencyInvoiceNumber(),
                "broj fakture MORA ostati na bookingu (audit trag)");
        assertNotNull(b.getAgencyInvoicedAt(), "datum fakturisanja se cuva");
        assertNotNull(b.getAgencyVoidedAt(), "datum storna se belezi");
        assertTrue(b.getAgencyVoidReason().contains("duplirano"),
                "razlog storna se belezi za audit");
    }

    @Test
    void void_bez_razloga_je_400() {
        Booking b = confirmedBooking(SettlementStatus.INVOICED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.voidAgencyInvoice(42L, "  "));
        assertEquals(400, ex.getStatusCode().value(),
                "razlog storna je obavezan za audit trag");
    }

    @Test
    void void_iz_ne_INVOICED_stanja_je_409() {
        Booking b = confirmedBooking(SettlementStatus.PAID);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.voidAgencyInvoice(42L, "test"));
        assertEquals(409, ex.getStatusCode().value(),
                "VOID mora ici iz INVOICED; PAID se prvo vraca u INVOICED");
    }

    @Test
    void status_INVOICED_u_READY_vise_nije_dozvoljen() {
        // Ranije je "storno" bio INVOICED->READY_FOR_INVOICE koji je brisao broj.
        // Sada je to VOID (poseban endpoint) - update-settlement-status vise ne
        // sme dopustati taj tok.
        Booking b = confirmedBooking(SettlementStatus.INVOICED);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0007");
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateSettlementStatus(42L, SettlementStatus.READY_FOR_INVOICE));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void statusPrelaz_neodozvoljen_baca_409() {
        Booking b = confirmedBooking(SettlementStatus.NEEDS_COSTS);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));

        // NEEDS_COSTS ne moze direktno u PAID
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.updateSettlementStatus(42L, SettlementStatus.PAID));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void statusPrelaz_paid_rollback_u_invoiced_brise_paidAt_ali_cuva_broj() {
        Booking b = confirmedBooking(SettlementStatus.PAID);
        b.setAgencyInvoiceNumber("ESC-AG-2026-0003");
        b.setAgencyPaidAt(java.time.LocalDateTime.now());
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(b));
        when(agencySettlementCalculator.calculate(b)).thenReturn(readyPreview());

        svc.updateSettlementStatus(42L, SettlementStatus.INVOICED);

        assertEquals(SettlementStatus.INVOICED, b.getSettlementStatus());
        assertNull(b.getAgencyPaidAt());
        assertEquals("ESC-AG-2026-0003", b.getAgencyInvoiceNumber());
    }
}
