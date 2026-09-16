package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Agency;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Promena agencije na terminu: potvrđene/završene rezervacije koje još nisu u zbirnoj fakturi
 * prelaze na novu agenciju (snimak se prepiše); one koje su već na njoj se ne diraju; skidanje
 * agencije ne dira rezervacije.
 */
@ExtendWith(MockitoExtension.class)
class AssignAgencyToDateTest {

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
    @Mock private com.escapii.service.impl.PartnerSlugFiller partnerSlugFiller;
    @Mock private InvoiceService invoiceService;
    @Mock private ConfirmationDocumentEmailService confirmationDocumentEmailService;
    @Mock private ConfirmationDocumentAutoSender confirmationDocumentAutoSender;
    @Mock private AgencySettlementCalculator agencySettlementCalculator;
    @Mock private BookingFinancialItemRepository bookingFinancialItemRepository;

    private AdminServiceImpl svc;
    private Agency sani;
    private Agency tst;
    private AvailableDate termin;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, partnerSlugFiller, new com.escapii.service.impl.VoucherLedger(), invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                agencySettlementCalculator, bookingFinancialItemRepository);
        sani = agencija(1L, "Sani Tours");
        tst = agencija(2L, "tst");
        termin = new AvailableDate();
        termin.setId(38L);
        termin.setAgency(sani);
        when(availableDateRepository.findById(38L)).thenReturn(Optional.of(termin));
    }

    private static Agency agencija(long id, String ime) {
        Agency a = new Agency();
        a.setId(id);
        a.setName(ime);
        return a;
    }

    private static Booking rezervacija(long id, String ref, BookingStatus status, Long snimakId, String snimakIme) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef(ref);
        b.setStatus(status);
        b.setAgencyIdSnapshot(snimakId);
        b.setAgencyNameSnapshot(snimakIme);
        return b;
    }

    @Test
    void promenaAgencije_prebacujeNefakturisaneRezervacije_aVecPrebaceneNeDira() {
        when(agencyRepository.findById(2L)).thenReturn(Optional.of(tst));
        Booking kodSani = rezervacija(10, "ESC-e0208350", BookingStatus.COMPLETED, 1L, "Sani Tours");
        Booking bezSnimka = rezervacija(11, "ESC-89000e2c", BookingStatus.CONFIRMED, null, null);
        Booking vecTst = rezervacija(12, "ESC-8ec058fc", BookingStatus.COMPLETED, 2L, "tst");
        when(bookingRepository.findOnDateNotInvoiced(38L)).thenReturn(List.of(kodSani, bezSnimka, vecTst));

        int n = svc.assignAgencyToDate(38L, 2L);

        assertEquals(2, n);
        assertSame(tst, termin.getAgency());
        verify(availableDateRepository).save(termin);
        for (Booking b : List.of(kodSani, bezSnimka)) {
            assertEquals(2L, b.getAgencyIdSnapshot(), b.getBookingRef());
            assertEquals("tst", b.getAgencyNameSnapshot(), b.getBookingRef());
            verify(bookingRepository).save(b);
        }
        verify(bookingRepository, never()).save(vecTst);
    }

    @Test
    void skidanjeAgencije_neDiraRezervacije() {
        int n = svc.assignAgencyToDate(38L, null);

        assertEquals(0, n);
        assertNull(termin.getAgency());
        verify(availableDateRepository).save(termin);
        verify(bookingRepository, never()).findOnDateNotInvoiced(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void nepostojecaAgencija_400_iNistaSeNeMenja() {
        when(agencyRepository.findById(9L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.assignAgencyToDate(38L, 9L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertSame(sani, termin.getAgency());
        verify(availableDateRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }
}
