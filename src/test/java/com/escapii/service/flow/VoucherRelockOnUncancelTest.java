package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.mapper.DestinationMapper;
import com.escapii.model.Agency;
import com.escapii.model.AvailableDate;
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
 * Prijavljen bag: rezervacija sa primenjenim vaučerom je otkazana (vaučer se
 * ispravno oslobodio → ACTIVE), a zatim je ta ISTA otkazana rezervacija ručno
 * vraćena nazad na CONFIRMED - vaučer je ostao ACTIVE umesto da se ponovo
 * zaključa, iako rezervacija i dalje nosi popust u svojoj ceni. To znači da bi
 * neko drugi mogao istovremeno da iskoristi isti vaučer za novu rezervaciju.
 */
@ExtendWith(MockitoExtension.class)
class VoucherRelockOnUncancelTest {

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
    @Mock private ConfirmationDocumentAutoSender confirmationDocumentAutoSender;

    private AdminServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new AdminServiceImpl(agencyRepository, availableDateRepository, destinationRepository, termDestinationRepository,
                bookingRepository, giftVoucherRepository, revealEventRepository, inquiryRepository,
                adminBookingMapper, destinationMapper, eventPublisher, waitlistService,
                availableDateService, inquiryService, airportLookupService, invoiceService,
                confirmationDocumentEmailService, confirmationDocumentAutoSender);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        // lenient - jedan test namerno nikad ne poziva save() na vaučeru (nema promene stanja)
        lenient().when(giftVoucherRepository.save(any(GiftVoucher.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking bookingWithVoucher(BookingStatus status, String code, int discount) {
        Booking b = new Booking();
        b.setId(7L);
        b.setBookingRef("ESC-test0007");
        b.setStatus(status);
        b.setNumberOfTravelers(2);
        b.setAppliedVoucherCode(code);
        b.setVoucherDiscount(discount);
        return b;
    }

    private AvailableDate slotWith(int availableSlots) {
        AvailableDate d = new AvailableDate();
        d.setId(50L);
        d.setAvailableSlots(availableSlots);
        // Agencija na terminu je preduslov da booking uopšte može u CONFIRMED
        // (guard u updateBookingStatus) - testovi ovde ne testiraju taj guard
        // nego vaučer logiku, pa termin uvek ima agenciju kao u produkciji.
        Agency a = new Agency();
        a.setId(1L);
        a.setName("Test agencija");
        d.setAgency(a);
        return d;
    }

    private GiftVoucher voucher(BigDecimal amount, BigDecimal usedAmount, VoucherStatus status) {
        GiftVoucher v = new GiftVoucher();
        v.setId(9L);
        v.setCode("ESC-AAAA-BBBB-CCCC");
        v.setAmount(amount);
        v.setUsedAmount(usedAmount);
        v.setStatus(status);
        return v;
    }

    @Test
    void otkazivanjeOslobadjaVaucerIBrisePrethodnoZakljucanje() {
        Booking b = bookingWithVoucher(BookingStatus.CONFIRMED, "ESC-AAAA-BBBB-CCCC", 100);
        when(bookingRepository.findWithDetailsById(7L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(7L)).thenReturn(Optional.of(slotWith(3)));
        GiftVoucher v = voucher(BigDecimal.valueOf(100), BigDecimal.valueOf(100), VoucherStatus.RESERVED);
        when(giftVoucherRepository.findByCodeForUpdate("ESC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(v));

        svc.updateBookingStatus(7L, BookingStatus.CANCELLED);

        assertEquals(VoucherStatus.ACTIVE, v.getStatus());
        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.ZERO));
        assertNull(v.getUsedInBookingRef(), "usedInBookingRef mora biti obrisan pri otkazivanju");
    }

    /** Glavni prijavljeni bag: CANCELLED → CONFIRMED mora ponovo zaključati vaučer. */
    @Test
    void ponovnoPotvrdjivanjeOtkazaneRezervacijeVracaVaucerNaRESERVED() {
        Booking b = bookingWithVoucher(BookingStatus.CANCELLED, "ESC-AAAA-BBBB-CCCC", 100);
        when(bookingRepository.findWithDetailsById(7L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(7L)).thenReturn(Optional.of(slotWith(3)));
        // Stanje posle otkazivanja: vaučer pušten nazad, pun iznos slobodan
        GiftVoucher v = voucher(BigDecimal.valueOf(100), BigDecimal.ZERO, VoucherStatus.ACTIVE);
        when(giftVoucherRepository.findByCodeForUpdate("ESC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(v));

        svc.updateBookingStatus(7L, BookingStatus.CONFIRMED);

        assertEquals(VoucherStatus.RESERVED, v.getStatus(),
                "vaučer mora ponovo biti zaključan kad se otkazana rezervacija vrati na CONFIRMED");
        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(7L, v.getUsedInBookingRef());
    }

    /**
     * Ako je vaučer u međuvremenu delimično potrošen od strane DRUGE rezervacije
     * dok je bio ACTIVE, re-lock sme da povrati samo ono što stvarno preostaje -
     * ne sme prebaciti usedAmount preko amount-a.
     */
    @Test
    void ponovnoPotvrdjivanjeOgranicavaSeNaPreostaliIznos() {
        Booking b = bookingWithVoucher(BookingStatus.CANCELLED, "ESC-AAAA-BBBB-CCCC", 100);
        when(bookingRepository.findWithDetailsById(7L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(7L)).thenReturn(Optional.of(slotWith(3)));
        // Neko drugi je već potrošio 60€ od vaučera dok je bio ACTIVE - ostaje samo 40€
        GiftVoucher v = voucher(BigDecimal.valueOf(100), BigDecimal.valueOf(60), VoucherStatus.ACTIVE);
        when(giftVoucherRepository.findByCodeForUpdate("ESC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(v));

        svc.updateBookingStatus(7L, BookingStatus.CONFIRMED);

        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.valueOf(100)),
                "usedAmount ne sme preći amount (100) čak ni posle re-locka");
        assertEquals(VoucherStatus.RESERVED, v.getStatus());
    }

    /** PENDING → CONFIRMED (bez prethodnog CANCELLED) i dalje ne sme dirati vaučer. */
    @Test
    void obicnaPotvrdaBezPrethodnogOtkazivanjaNeMenjaVaucer() {
        Booking b = bookingWithVoucher(BookingStatus.PENDING, "ESC-AAAA-BBBB-CCCC", 100);
        when(bookingRepository.findWithDetailsById(7L)).thenReturn(Optional.of(b));
        when(availableDateRepository.findByBookingId(7L)).thenReturn(Optional.of(slotWith(3)));
        GiftVoucher v = voucher(BigDecimal.valueOf(100), BigDecimal.valueOf(100), VoucherStatus.RESERVED);
        when(giftVoucherRepository.findByCodeForUpdate("ESC-AAAA-BBBB-CCCC")).thenReturn(Optional.of(v));

        svc.updateBookingStatus(7L, BookingStatus.CONFIRMED);

        verify(giftVoucherRepository, never()).save(any());
        assertEquals(VoucherStatus.RESERVED, v.getStatus());
    }
}
