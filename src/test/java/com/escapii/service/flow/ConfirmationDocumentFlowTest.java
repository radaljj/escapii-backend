package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Booking;
import com.escapii.model.RevealEvent;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Dokument rezervacije nosi destinaciju u naslovu mejla, pa dva pravila
 * moraju važiti u svakom trenutku:
 *
 *   1. Ne sme stići pre nego što kupac otvori reveal (destinacija ne sme
 *      procuriti ranije). Vazi jednako i za Reveal Box - i on sad dobija
 *      digitalni reveal, kutija je fizicki dodatak.
 *   2. "Poslato" u panelu mora značiti da je mejl stvarno otišao.
 *
 * DestinationSecrecyTest i SentFlagTruthfulnessTest čuvaju ova pravila kroz
 * izvorni kod (protiv budućih regresija u strukturi). Ovde se ista pravila
 * proveravaju kroz ponašanje, sa mockovanim zavisnostima.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmationDocumentFlowTest {

    @Mock private com.escapii.repository.AgencyRepository agencyRepository;
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
    @Mock private com.escapii.service.AgencySettlementCalculator agencySettlementCalculator;
    @Mock private com.escapii.repository.BookingFinancialItemRepository bookingFinancialItemRepository;
    @Mock private com.escapii.repository.AgencyInvoiceSequenceRepository agencyInvoiceSequenceRepository;

    private AdminServiceImpl svc;
    private ConfirmationDocumentAutoSender autoSender;

    @BeforeEach
    void setUp() {
        // Realan autoSender preko mockovanih zavisnosti - da testiramo pravu logiku
        // koja spaja pravilo (canReceive) i slanje na jednom mestu.
        autoSender = new ConfirmationDocumentAutoSender(
                bookingRepository, revealEventRepository, confirmationDocumentEmailService);
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService, autoSender,
                agencySettlementCalculator, bookingFinancialItemRepository,
                agencyInvoiceSequenceRepository);
    }

    private Booking bookingWithDocument(boolean hasRevealBox) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test1234");
        b.setConfirmationDocument(new byte[]{1, 2, 3});
        b.setHasRevealBox(hasRevealBox);
        return b;
    }

    // ── resendConfirmationDocument: brana "reveal otvoren" ──────────────────

    @Test
    void rucnoSlanjeOdbijaAkoRevealNijeOtvoren() {
        Booking b = bookingWithDocument(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(revealEventRepository.findByBookingRef("ESC-test1234")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> svc.resendConfirmationDocument(1L));
        verifyNoInteractions(confirmationDocumentEmailService);
    }

    @Test
    void rucnoSlanjeRadiPosleOtvorenogReveala() {
        Booking b = bookingWithDocument(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(revealEventRepository.findByBookingRef("ESC-test1234"))
                .thenReturn(Optional.of(new RevealEvent("ESC-test1234")));
        when(confirmationDocumentEmailService.sendConfirmationDocument(b)).thenReturn(true);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.resendConfirmationDocument(1L);

        assertNotNull(b.getConfirmationSentAt());
    }

    /**
     * Box korisnik ceka revealSentAt (T-2) umesto RevealEvent - kutija je vec
     * otkrila destinaciju pa nema smisla cekati klik. Bez revealSentAt-a je 409.
     */
    @Test
    void revealBoxBezRevealSentAtOdbija() {
        Booking b = bookingWithDocument(true);
        // revealSentAt = null (nije stigao T-2 jos)
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class, () -> svc.resendConfirmationDocument(1L));
        verifyNoInteractions(confirmationDocumentEmailService);
    }

    /** Regresija: box + revealSentAt dobija dokument bez klika na RevealEvent. */
    @Test
    void revealBoxSaRevealSentAtDobijaDokument() {
        Booking b = bookingWithDocument(true);
        b.setRevealSentAt(java.time.LocalDateTime.now());
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(confirmationDocumentEmailService.sendConfirmationDocument(b)).thenReturn(true);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.resendConfirmationDocument(1L);

        assertNotNull(b.getConfirmationSentAt());
        // Box korisnik ne treba da bude proveravan kroz RevealEvent - kutija je vec stigla
        verifyNoInteractions(revealEventRepository);
    }

    /** Srž zaštite: propalo slanje ne sme upisati vreme. */
    @Test
    void rucnoSlanjePropadaNeUpisujeSentAt() {
        Booking b = bookingWithDocument(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(revealEventRepository.findByBookingRef("ESC-test1234"))
                .thenReturn(Optional.of(new RevealEvent("ESC-test1234")));
        when(confirmationDocumentEmailService.sendConfirmationDocument(b)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> svc.resendConfirmationDocument(1L));

        assertNull(b.getConfirmationSentAt());
        verify(bookingRepository, never()).save(any());
    }

    // ── uploadConfirmationDocument: auto-slanje ako je reveal već viđen ─────

    private MultipartFile pdf() {
        return new MockMultipartFile("file", "rez.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    @Test
    void uploadPosleVidjenogRevealaSaljeOdmah() throws Exception {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-test1234"); b.setHasRevealBox(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(revealEventRepository.findByBookingRef("ESC-test1234"))
                .thenReturn(Optional.of(new RevealEvent("ESC-test1234")));
        when(confirmationDocumentEmailService.sendConfirmationDocument(any())).thenReturn(true);

        svc.uploadConfirmationDocument(1L, pdf());

        assertNotNull(b.getConfirmationSentAt());
    }

    @Test
    void uploadPreRevealaCekaBezSlanja() throws Exception {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-test1234"); b.setHasRevealBox(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(revealEventRepository.findByBookingRef("ESC-test1234")).thenReturn(Optional.empty());

        svc.uploadConfirmationDocument(1L, pdf());

        assertNull(b.getConfirmationSentAt());
        verifyNoInteractions(confirmationDocumentEmailService);
    }

    /** Box korisnik: upload posle T-2 (revealSentAt postavljen) → dokument ide odmah. */
    @Test
    void uploadBoxSaRevealSentAtSaljeOdmah() throws Exception {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-test1234"); b.setHasRevealBox(true);
        b.setRevealSentAt(java.time.LocalDateTime.now());
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(confirmationDocumentEmailService.sendConfirmationDocument(any())).thenReturn(true);

        svc.uploadConfirmationDocument(1L, pdf());

        assertNotNull(b.getConfirmationSentAt());
        // Box korisnik: nema smisla proveravati RevealEvent, kutija je vec otkrila destinaciju
        verifyNoInteractions(revealEventRepository);
    }

    /** Box korisnik: upload pre T-2 (nema revealSentAt) → ceka scheduler. */
    @Test
    void uploadBoxPreRevealSentAtCeka() throws Exception {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-test1234"); b.setHasRevealBox(true);
        // revealSentAt = null
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.uploadConfirmationDocument(1L, pdf());

        assertNull(b.getConfirmationSentAt());
        verifyNoInteractions(confirmationDocumentEmailService);
        verifyNoInteractions(revealEventRepository);
    }

    /**
     * Srž zaštite: ako je reveal viđen ali slanje pukne (npr. provajder odbio),
     * confirmationSentAt MORA ostati prazan - inače panel laže da je stiglo.
     */
    @Test
    void uploadPosleVidjenogRevealaAliSlanjePropadaOstajeNeposlat() throws Exception {
        Booking b = new Booking();
        b.setId(1L); b.setBookingRef("ESC-test1234"); b.setHasRevealBox(false);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(revealEventRepository.findByBookingRef("ESC-test1234"))
                .thenReturn(Optional.of(new RevealEvent("ESC-test1234")));
        when(confirmationDocumentEmailService.sendConfirmationDocument(any())).thenReturn(false);

        svc.uploadConfirmationDocument(1L, pdf());

        assertNull(b.getConfirmationSentAt(), "propalo slanje ne sme upisati vreme");
    }
}
