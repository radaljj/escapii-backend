package com.escapii.service.flow;

import com.escapii.mapper.AdminBookingMapper;
import com.escapii.model.*;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.repository.InvoiceSequenceRepository;
import com.escapii.service.email.InvoiceEmailService;
import com.escapii.service.impl.InvoiceServiceImpl;
import com.escapii.service.invoice.InvoicePdfService;
import com.escapii.service.voucher.QrCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Profaktura (rezervacija i vaučer) je novac - broj i vreme slanja se smeju
 * evidentirati SAMO ako je mejl stvarno otišao. Do danas se vreme upisivalo
 * PRE poziva za slanje, pa bi propalo slanje ostalo nevidljivo: u panelu
 * "poslato", kod kupca ništa, bez ikakvog načina da se primeti.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceSendOutcomeTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherRepository giftVoucherRepository;
    @Mock private InvoiceSequenceRepository invoiceSequenceRepository;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private InvoiceEmailService invoiceEmailService;
    @Mock private QrCodeGenerator qrCodeGenerator;
    @Mock private AdminBookingMapper adminBookingMapper;

    private InvoiceServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new InvoiceServiceImpl(bookingRepository, giftVoucherRepository, invoiceSequenceRepository,
                invoicePdfService, invoiceEmailService, qrCodeGenerator, adminBookingMapper);
        // @Value polja se van Spring konteksta ne popunjavaju - postave se ručno
        for (String field : new String[]{"companyName","companyAddress","companyPib","companyMb",
                "companyAccount","companyBank","companyEmail","companyWebsite"}) {
            ReflectionTestUtils.setField(svc, field, "test");
        }
        ReflectionTestUtils.setField(svc, "invoiceDueDays", 3);
    }

    private Booking pendingBooking() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingRef("ESC-test1234");
        b.setStatus(BookingStatus.PENDING);
        b.setFirstName("Marko"); b.setLastName("Marković");
        b.setEmail("marko@example.com"); b.setPhone("+381601234567");
        b.setTotalPriceAll(500);
        AvailableDate d = new AvailableDate();
        d.setDepartureDate(LocalDate.now().plusDays(30));
        d.setReturnDate(LocalDate.now().plusDays(33));
        b.setSelectedDate(d);
        return b;
    }

    private void stubSequenceAndPdf() {
        InvoiceSequence seq = new InvoiceSequence(LocalDate.now().getYear());
        when(invoiceSequenceRepository.findByYear(anyInt())).thenReturn(Optional.of(seq));
        when(invoiceSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(qrCodeGenerator.pngDataUri(anyString(), anyInt())).thenReturn("data:image/png;base64,x");
        when(invoicePdfService.generate(any())).thenReturn(new byte[]{1, 2, 3});
        // bookingRepository.save/giftVoucherRepository.save se namerno NE stubuju
        // ovde - na propaloj putanji se ne pozivaju uopšte (to je i poenta testa),
        // a strogi Mockito bi prijavio "unnecessary stubbing" za testove koji ih
        // ne dosegnu. Testovi uspešne putanje stubuju save po potrebi sami.
    }

    @Test
    void uspesnoSlanjeUpisujeBrojIVreme() {
        Booking b = pendingBooking();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        stubSequenceAndPdf();
        when(invoiceEmailService.sendInvoiceToClient(eq(b), any(), anyString())).thenReturn(true);

        svc.sendInvoice(1L);

        assertNotNull(b.getInvoiceSentAt(), "uspešno slanje mora upisati vreme");
        assertNotNull(b.getInvoiceNumber(), "uspešno slanje mora upisati broj profakture");
    }

    /** Srž zaštite: propalo slanje ne sme ostaviti utisak da je profaktura otišla. */
    @Test
    void propaloSlanjeNeUpisujeNistaIVracaGresku() {
        Booking b = pendingBooking();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));
        stubSequenceAndPdf();
        when(invoiceEmailService.sendInvoiceToClient(eq(b), any(), anyString())).thenReturn(false);

        var ex = assertThrows(ResponseStatusException.class, () -> svc.sendInvoice(1L));
        assertEquals(502, ex.getStatusCode().value());

        assertNull(b.getInvoiceSentAt(), "propalo slanje ne sme upisati vreme - inače je 'poslato' laž");
        assertNull(b.getInvoiceNumber(), "propalo slanje ne sme potrošiti broj profakture");
    }

    @Test
    void nePendingRezervacijaOdbijaSePreSlanja() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class, () -> svc.sendInvoice(1L));
        verifyNoInteractions(invoiceEmailService);
    }

    @Test
    void vecPoslataProfakturaSeNePonavljaAutomatski() {
        Booking b = pendingBooking();
        b.setInvoiceSentAt(LocalDateTime.now());
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class, () -> svc.sendInvoice(1L));
        verifyNoInteractions(invoiceEmailService);
    }

    // ── Isti uslovi važe i za vaučer profakturu ──────────────────────────────

    private GiftVoucher pendingVoucher() {
        GiftVoucher v = new GiftVoucher();
        v.setId(9L);
        v.setCode("ESC-AAAA-BBBB-CCCC");
        v.setStatus(VoucherStatus.PENDING);
        v.setAmount(BigDecimal.valueOf(100));
        v.setBuyerEmail("kupac@example.com");
        v.setBuyerName("Ana Anić");
        return v;
    }

    @Test
    void vaucerUspesnoSlanjeUpisujeBrojIVreme() {
        GiftVoucher v = pendingVoucher();
        when(giftVoucherRepository.findById(9L)).thenReturn(Optional.of(v));
        stubSequenceAndPdf();
        when(giftVoucherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceEmailService.sendVoucherInvoiceToClient(eq(v), any(), anyString())).thenReturn(true);

        svc.sendVoucherInvoice(9L);

        assertNotNull(v.getInvoiceSentAt());
        assertNotNull(v.getInvoiceNumber());
    }

    @Test
    void vaucerPropaloSlanjeNeUpisujeNista() {
        GiftVoucher v = pendingVoucher();
        when(giftVoucherRepository.findById(9L)).thenReturn(Optional.of(v));
        stubSequenceAndPdf();
        when(invoiceEmailService.sendVoucherInvoiceToClient(eq(v), any(), anyString())).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> svc.sendVoucherInvoice(9L));

        assertNull(v.getInvoiceSentAt());
        assertNull(v.getInvoiceNumber());
        verify(giftVoucherRepository, never()).save(any());
    }
}
