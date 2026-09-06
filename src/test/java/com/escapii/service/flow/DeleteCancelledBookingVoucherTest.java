package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Nalaz iz revizije: dupli povraćaj vaučera.
 *
 * Iznos vaučera se vraća na TRI mesta koja jedno za drugo ne znaju - pri
 * otkazivanju (updateBookingStatus), pri brisanju (deleteBooking) i u
 * scheduleru. Niz "otkaži pa obriši" nepotvrđenu rezervaciju je zato oduzimao
 * isti iznos dvaput: vaučer od 300 € podeljen na dve rezervacije po 150 € posle
 * toga prijavljuje 300 € slobodno, iako druga rezervacija i dalje nosi svojih
 * 150 € popusta. Kupac istim kodom rezerviše još 300 € - gubitak 150 € po incidentu.
 *
 * Brisanje POTVRĐENE rezervacije je već blokirano, pa je otkazana jedini put do
 * duplog vraćanja - i baš nju deleteBooking sada preskače.
 */
@ExtendWith(MockitoExtension.class)
class DeleteCancelledBookingVoucherTest {

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
    @Mock private AgencyInvoiceSequenceRepository agencyInvoiceSequenceRepository;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, partnerSlugFiller, new com.escapii.service.impl.VoucherLedger(), invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender,
                agencySettlementCalculator, bookingFinancialItemRepository,
                agencyInvoiceSequenceRepository);
    }

    private static final String KOD = "ESC-AAAA-BBBB-CCCC";

    /**
     * @param zakljucano koliko rezervacija STVARNO drži na vaučeru; {@code null} znači
     *                   da ne drži ništa - tako izgleda otkazana, kojoj je iznos već
     *                   vraćen. Ta razlika je i cela zaštita od duplog vraćanja.
     */
    private Booking rezervacija(BookingStatus status, BookingStatus stariStatus, BigDecimal zakljucano) {
        Booking b = new Booking();
        b.setId(7L);
        b.setBookingRef("ESC-test0007");
        b.setStatus(status);
        b.setOldStatus(stariStatus);
        b.setNumberOfTravelers(2);
        b.setAppliedVoucherCode(KOD);
        b.setVoucherDiscount(150);
        b.setVoucherLockedAmount(zakljucano);
        return b;
    }

    /** Vaučer od 300 € kome je 150 € VEĆ vraćeno pri otkazivanju: koristi se još 150 € na drugoj rezervaciji. */
    private GiftVoucher vaucerPosleOtkazivanja() {
        GiftVoucher v = new GiftVoucher();
        v.setId(9L);
        v.setCode(KOD);
        v.setAmount(BigDecimal.valueOf(300));
        v.setUsedAmount(BigDecimal.valueOf(150));
        v.setStatus(VoucherStatus.ACTIVE);
        return v;
    }

    /** Glavni nalaz: rezervacija koja je vec otkazana ne sme ponovo da vrati iznos. */
    @Test
    void brisanjeOtkazaneRezervacijeNeVracaVaucerDrugiPut() {
        Booking otkazana = rezervacija(BookingStatus.CANCELLED, BookingStatus.PENDING, null);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(otkazana));

        svc.deleteBooking(7L);

        // Vaučer se uopšte ne dira: rezervacija ne drži ništa (voucherLockedAmount je null,
        // obrisan pri otkazivanju), pa nema šta da se vrati - i nema razloga ni za lock.
        verify(giftVoucherRepository, never()).findByCodeForUpdate(anyString());
        verify(giftVoucherRepository, never()).save(any(GiftVoucher.class));
        verify(bookingRepository).deleteById(7L);
    }

    /** Kontrola: nepotvrđena a NEotkazana rezervacija i dalje vraća iznos - tačno jednom. */
    @Test
    void brisanjeNepotvrdjeneRezervacijeVracaVaucerJednom() {
        Booking pending = rezervacija(BookingStatus.PENDING, null, BigDecimal.valueOf(150));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(pending));
        GiftVoucher v = vaucerPosleOtkazivanja();   // 150 od 300 u upotrebi - ovom rezervacijom
        when(giftVoucherRepository.findByCodeForUpdate(KOD)).thenReturn(Optional.of(v));
        when(giftVoucherRepository.save(any(GiftVoucher.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.deleteBooking(7L);

        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.ZERO),
                "150 € ove rezervacije se vraća - i ni evro više");
        assertEquals(VoucherStatus.ACTIVE, v.getStatus());
        verify(bookingRepository).deleteById(7L);
    }

    /**
     * Nalaz protivprovere: kroz API se otkazana moze gurnuti u COMPLETED (kontroler prima
     * svaku vrednost enuma) i tako zaobici guard za CANCELLED. Zavrseno putovanje je
     * istorija - brisanje je blokirano, pa taj put vise ne postoji.
     */
    @Test
    void brisanjeZavrseneJeBlokiranoIKadJePreTogaBilaOtkazana() {
        Booking otkazanaPaZavrsena = rezervacija(BookingStatus.COMPLETED, BookingStatus.CANCELLED, null);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(otkazanaPaZavrsena));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> svc.deleteBooking(7L));
        verify(bookingRepository, never()).deleteById(anyLong());
        verify(giftVoucherRepository, never()).findByCodeForUpdate(anyString());
    }

    /** Brisanje potvrđene ostaje blokirano - to je i razlog što je otkazana jedini rizični slučaj. */
    @Test
    void brisanjePotvrdjeneJeIDaljeBlokirano() {
        Booking potvrdjenaPaOtkazana = rezervacija(BookingStatus.CANCELLED, BookingStatus.CONFIRMED, null);
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(potvrdjenaPaOtkazana));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> svc.deleteBooking(7L));
        verify(bookingRepository, never()).deleteById(anyLong());
        verify(giftVoucherRepository, never()).findByCodeForUpdate(anyString());
    }
}
