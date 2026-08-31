package com.escapii.service.flow;

import com.escapii.dto.AgencyDashboardSummary;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Booking;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Dashboard summary razdvaja projekciju od stvarno fakturisanog i naplacenog.
 * Bez ovoga jedan zbir "Escapii zaradio" bi mesao PENDING/NEEDS_COSTS sa
 * INVOICED/PAID i davao pogresnu sliku knjigovodstvu.
 */
@ExtendWith(MockitoExtension.class)
class AgencyDashboardSummaryTest {

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
    }

    private Booking bookingWith(SettlementStatus st) {
        Booking b = new Booking();
        b.setId((long) st.ordinal() + 1);
        b.setSettlementStatus(st);
        return b;
    }

    private AgencySettlementResponse response(BigDecimal earnings) {
        return AgencySettlementResponse.builder()
                .escapiiEarnings(earnings)
                .build();
    }

    @Test
    void summary_razdvaja_projekciju_fakturisano_naplaceno() {
        Booking needs  = bookingWith(SettlementStatus.NEEDS_COSTS);
        Booking ready  = bookingWith(SettlementStatus.READY_FOR_INVOICE);
        Booking inv    = bookingWith(SettlementStatus.INVOICED);
        Booking paid   = bookingWith(SettlementStatus.PAID);
        Booking voided = bookingWith(SettlementStatus.VOIDED);

        when(bookingRepository.findForAgencyDashboard(any(), any(), any(), any()))
                .thenReturn(List.of(needs, ready, inv, paid, voided));
        when(agencySettlementCalculator.calculate(needs)).thenReturn(response(new BigDecimal("10.00")));
        when(agencySettlementCalculator.calculate(ready)).thenReturn(response(new BigDecimal("20.00")));
        when(agencySettlementCalculator.calculate(inv)).thenReturn(response(new BigDecimal("30.00")));
        when(agencySettlementCalculator.calculate(paid)).thenReturn(response(new BigDecimal("40.00")));
        when(agencySettlementCalculator.calculate(voided)).thenReturn(response(new BigDecimal("999.00")));

        AgencyDashboardSummary s = svc.agencyDashboardSummary(null, null, null);

        // Projekcija = READY + INVOICED + PAID (20 + 30 + 40 = 90) - bez NEEDS/VOIDED
        assertEquals(new BigDecimal("90.00"), s.getProjectedEscapiiTotal());
        assertEquals(3, s.getProjectedCount());
        // Fakturisano samo INVOICED
        assertEquals(new BigDecimal("30.00"), s.getInvoicedEscapiiTotal());
        assertEquals(1, s.getInvoicedCount());
        // Naplaceno samo PAID
        assertEquals(new BigDecimal("40.00"), s.getPaidEscapiiTotal());
        assertEquals(1, s.getPaidCount());
        // Brojaci
        assertEquals(1, s.getNeedsCostsCount());
        assertEquals(1, s.getReadyForInvoiceCount());
        assertEquals(1, s.getVoidedCount());
    }

    @Test
    void summary_bez_bookinga_daje_nule() {
        when(bookingRepository.findForAgencyDashboard(any(), any(), any(), any()))
                .thenReturn(List.of());

        AgencyDashboardSummary s = svc.agencyDashboardSummary(null, null, null);
        assertEquals(0, s.getProjectedEscapiiTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.getProjectedCount());
        assertEquals(0, s.getInvoicedCount());
        assertEquals(0, s.getPaidCount());
    }
}
