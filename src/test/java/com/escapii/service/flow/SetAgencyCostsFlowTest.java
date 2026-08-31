package com.escapii.service.flow;

import com.escapii.dto.AgencyCostsRequest;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.AllocationType;
import com.escapii.model.Booking;
import com.escapii.model.BookingFinancialItem;
import com.escapii.model.BookingStatus;
import com.escapii.model.ItemType;
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
import com.escapii.service.AirportLookupService;
import com.escapii.service.AvailableDateService;
import com.escapii.service.CustomDateInquiryService;
import com.escapii.service.InvoiceService;
import com.escapii.service.WaitlistService;
import com.escapii.service.email.ConfirmationDocumentEmailService;
import com.escapii.service.impl.AdminServiceImpl;
import com.escapii.service.impl.AgencySettlementCalculatorImpl;
import com.escapii.service.impl.ConfirmationDocumentAutoSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * setAgencyCosts (PUT /agency-costs) mora:
 *   (a) vratiti response sa AKTUELNIM settlementStatus posle save-a (ne stari),
 *   (b) podrzati brisanje troska preko clear polja (null i dalje = "ne menjaj"),
 *   (c) ne dozvoliti prelaz u READY_FOR_INVOICE ako booking nije CONFIRMED.
 */
@ExtendWith(MockitoExtension.class)
class SetAgencyCostsFlowTest {

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
    @Mock private BookingFinancialItemRepository bookingFinancialItemRepository;
    @Mock private AgencyInvoiceSequenceRepository agencyInvoiceSequenceRepository;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        // Koristimo pravi kalkulator - da testiramo pravi end-to-end tok statusa
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                new AgencySettlementCalculatorImpl(), bookingFinancialItemRepository,
                agencyInvoiceSequenceRepository);
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking bookingWithBase(BookingStatus status, SettlementStatus settlement) {
        Booking b = new Booking();
        b.setId(11L);
        b.setBookingRef("ESC-test0011");
        b.setStatus(status);
        b.setSettlementStatus(settlement);
        b.setTotalPriceAll(359);
        b.setVoucherDiscount(0);
        b.setFinancialItems(new ArrayList<>());

        BookingFinancialItem base = new BookingFinancialItem();
        base.setBooking(b);
        base.setItemType(ItemType.BASE_PACKAGE);
        base.setAllocationType(AllocationType.MARGIN_50_50);
        base.setQuantity(1);
        base.setUnitCustomerPrice(new BigDecimal("359.00"));
        base.setCustomerTotal(new BigDecimal("359.00"));
        b.getFinancialItems().add(base);
        return b;
    }

    // ── Fix 1: response mora imati sinhronizovan status ─────────────────

    @Test
    void unosTroskovaNaConfirmed_response_ima_READY_FOR_INVOICE_status() {
        Booking b = bookingWithBase(BookingStatus.CONFIRMED, SettlementStatus.NEEDS_COSTS);
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(b));

        AgencyCostsRequest req = new AgencyCostsRequest();
        req.setFlightAgencyCost(new BigDecimal("150.00"));
        req.setHotelAgencyCost(new BigDecimal("70.00"));

        AgencySettlementResponse r = svc.setAgencyCosts(11L, req);

        assertEquals(SettlementStatus.READY_FOR_INVOICE, b.getSettlementStatus(),
                "booking mora biti u READY_FOR_INVOICE posle save-a");
        assertEquals(SettlementStatus.READY_FOR_INVOICE, r.getSettlementStatus(),
                "response ne sme vratiti stari NEEDS_COSTS - API bi lagao");
    }

    // ── Fix 3: PENDING ne moze u READY ──────────────────────────────────

    @Test
    void unosTroskovaNaPending_ostaje_NEEDS_COSTS() {
        Booking b = bookingWithBase(BookingStatus.PENDING, SettlementStatus.NEEDS_COSTS);
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(b));

        AgencyCostsRequest req = new AgencyCostsRequest();
        req.setFlightAgencyCost(new BigDecimal("150.00"));
        req.setHotelAgencyCost(new BigDecimal("70.00"));

        AgencySettlementResponse r = svc.setAgencyCosts(11L, req);

        assertEquals(SettlementStatus.NEEDS_COSTS, b.getSettlementStatus(),
                "PENDING kupac nije platio - fakturisanje agenciji ne sme biti moguce");
        assertFalse(r.isReadyForInvoice());
    }

    // ── Fix 2: clear brise troskove i vraca u NEEDS_COSTS ───────────────

    @Test
    void clear_brise_trosak_i_vraca_status_u_NEEDS_COSTS() {
        Booking b = bookingWithBase(BookingStatus.CONFIRMED, SettlementStatus.READY_FOR_INVOICE);
        // simulacija stanja posle prethodnog unosa: troskovi su vec popunjeni
        BookingFinancialItem base = b.getFinancialItems().get(0);
        base.setFlightAgencyCost(new BigDecimal("150.00"));
        base.setHotelAgencyCost(new BigDecimal("70.00"));
        base.setAgencyCost(new BigDecimal("220.00"));
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(b));

        AgencyCostsRequest req = new AgencyCostsRequest();
        req.setClear(Set.of(ItemType.BASE_PACKAGE));

        AgencySettlementResponse r = svc.setAgencyCosts(11L, req);

        assertNull(base.getAgencyCost(), "clear mora obrisati agencyCost");
        assertNull(base.getFlightAgencyCost(), "clear na BASE_PACKAGE brise i flight");
        assertNull(base.getHotelAgencyCost(), "clear na BASE_PACKAGE brise i hotel");
        assertEquals(SettlementStatus.NEEDS_COSTS, b.getSettlementStatus(),
                "brisanje troska mora vratiti READY_FOR_INVOICE u NEEDS_COSTS");
        assertEquals(SettlementStatus.NEEDS_COSTS, r.getSettlementStatus(),
                "response mora prijaviti novi status");
    }

    @Test
    void null_polje_znaci_ne_menjaj_a_ne_obrisi() {
        Booking b = bookingWithBase(BookingStatus.CONFIRMED, SettlementStatus.READY_FOR_INVOICE);
        BookingFinancialItem base = b.getFinancialItems().get(0);
        base.setFlightAgencyCost(new BigDecimal("150.00"));
        base.setHotelAgencyCost(new BigDecimal("70.00"));
        base.setAgencyCost(new BigDecimal("220.00"));
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(b));

        AgencyCostsRequest req = new AgencyCostsRequest();
        // sva polja null, nema clear-a - request je no-op

        svc.setAgencyCosts(11L, req);

        assertEquals(new BigDecimal("220.00"), base.getAgencyCost(),
                "null polje ne sme dirati postojecu vrednost - to razlikuje 'ne salji' od 'obrisi'");
    }
}
