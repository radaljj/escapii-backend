package com.escapii.service.impl;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Booking;
import com.escapii.model.Destination;
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
 * Dodela destinacije iz panela (PATCH /api/admin/bookings/{id}/destination): kupac je platio da
 * NE ide u isključenu destinaciju, pa se ona ne sme dodeliti ni kad je ukucana drugačije (velika
 * slova, bez dijakritika, suvišni razmaci, englesko ime). Panel to označava u padajućoj listi,
 * ali polje prima i slobodan tekst - provera na backendu je poslednja brana.
 */
@ExtendWith(MockitoExtension.class)
class SetDestinationExclusionGuardTest {

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
    @Mock private PartnerSlugFiller partnerSlugFiller;
    @Mock private InvoiceService invoiceService;
    @Mock private ConfirmationDocumentEmailService confirmationDocumentEmailService;
    @Mock private ConfirmationDocumentAutoSender confirmationDocumentAutoSender;
    @Mock private AgencySettlementCalculator agencySettlementCalculator;
    @Mock private BookingFinancialItemRepository bookingFinancialItemRepository;

    private AdminServiceImpl svc;
    private Booking rezervacija;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, partnerSlugFiller, new VoucherLedger(), invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                agencySettlementCalculator, bookingFinancialItemRepository);
        rezervacija = new Booking();
        rezervacija.setId(7L);
        rezervacija.setBookingRef("ESC-test0007");
        rezervacija.setExcludedDestination1(destinacija("Barcelona", "Barcelona"));
        rezervacija.setExcludedDestination2(destinacija("Beč", "Vienna"));
        rezervacija.setExcludedDestination3(destinacija("Sveti Đorđe", null));
    }

    private void uBazi() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(rezervacija));
    }

    private static Destination destinacija(String ime, String imeEn) {
        Destination d = new Destination();
        d.setName(ime);
        d.setNameEn(imeEn);
        return d;
    }

    @Test
    void iskljucenaDestinacijaSeNeMozeDodeliti_niDrugacijeNapisana() {
        uBazi();
        for (String uneto : List.of("Barcelona", "barcelona", "  BARCELONA ", "Bec", "beč", "Vienna", "VIENNA",
                                    "sveti dorde", "Sveti Đorđe", "SVETI  ĐORĐE")) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> svc.setDestination(7L, uneto, false), "prošlo: " + uneto);
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode(), uneto);
            assertTrue(ex.getReason().contains("isključio"), ex.getReason());
        }
        assertNull(rezervacija.getAssignedDestination(), "ništa ne sme biti upisano");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    /** force služi samo za promenu posle poslatog reveala - isključenje ne zaobilazi. */
    @Test
    void forceNeZaobilaziIskljucenje() {
        uBazi();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> svc.setDestination(7L, "Vienna", true));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    /** Slično ime nije isto ime - provera ne sme lažno da blokira; brisanje destinacije prolazi. */
    @Test
    void drugaDestinacijaProlazi_iBrisanjeProlazi() {
        uBazi();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.setDestination(7L, "Barcelos", false);
        assertEquals("Barcelos", rezervacija.getAssignedDestination());

        svc.setDestination(7L, "Lisabon", false);
        assertEquals("Lisabon", rezervacija.getAssignedDestination());

        svc.setDestination(7L, "", false);   // brisanje: prazan unos se upisuje kao prazan (postojeće ponašanje)
        assertTrue(rezervacija.getAssignedDestination() == null || rezervacija.getAssignedDestination().isEmpty());
    }

    @Test
    void kljucImenaSkidaDijakritikeRazmakeIVelikaSlova() {
        assertEquals("svetidorde", AdminServiceImpl.kljucImena(" Sveti  Đorđe "));
        assertEquals("cacak", AdminServiceImpl.kljucImena("Čačak"));
        assertEquals("saopaulo", AdminServiceImpl.kljucImena("São Paulo"));
        assertEquals("bec", AdminServiceImpl.kljucImena("BEČ"));
        assertEquals("", AdminServiceImpl.kljucImena(null));
        assertEquals("", AdminServiceImpl.kljucImena("  "));
    }
}
