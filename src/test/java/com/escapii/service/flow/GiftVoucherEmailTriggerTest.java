package com.escapii.service.flow;

import com.escapii.dto.GiftVoucherRequest;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.email.GiftVoucherEmailService;
import com.escapii.service.impl.GiftVoucherServiceImpl;
import com.escapii.service.voucher.VoucherPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Poklon vaučer prolazi kroz dva mejl okidača: tim se obaveštava odmah pri
 * kupovini (upit za odobrenje), kupac dobija PDF kod tek pri aktivaciji.
 */
@ExtendWith(MockitoExtension.class)
class GiftVoucherEmailTriggerTest {

    @Mock private GiftVoucherRepository voucherRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherEmailService emailService;
    @Mock private VoucherPdfService voucherPdfService;

    private GiftVoucherServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new GiftVoucherServiceImpl(voucherRepository, bookingRepository, emailService, voucherPdfService);
    }

    @Test
    void kupovinaVaucheraUvekObavestavaTim() {
        when(voucherRepository.save(any())).thenAnswer(inv -> {
            GiftVoucher v = inv.getArgument(0);
            v.setId(1L);
            return v;
        });
        GiftVoucherRequest req = new GiftVoucherRequest(100, "kupac@example.com", "Ana Anić", "Srećan rođendan!");

        svc.createVoucher(req);

        ArgumentCaptor<GiftVoucher> captor = ArgumentCaptor.forClass(GiftVoucher.class);
        verify(emailService).sendTeamAlert(captor.capture());
        assertEquals(1L, captor.getValue().getId(), "tim mora dobiti SAČUVAN vaučer (sa ID-jem)");
    }

    @Test
    void aktivacijaSaljePdfKupcu() {
        GiftVoucher v = new GiftVoucher();
        v.setId(5L);
        v.setStatus(VoucherStatus.PENDING);
        v.setAmount(BigDecimal.valueOf(100));
        v.setCode("ESC-AAAA-BBBB-CCCC");
        v.setBuyerEmail("kupac@example.com");
        when(voucherRepository.findById(5L)).thenReturn(Optional.of(v));
        when(voucherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(voucherPdfService.generate(any())).thenReturn(new byte[]{1, 2, 3});

        svc.activateVoucher(5L);

        verify(emailService).sendVoucherPdfToBuyer(eq(v), any());
        assertEquals(VoucherStatus.ACTIVE, v.getStatus());
    }

    /**
     * Poznat, namerno neizmenjen gap (nije deo ove izmene): PDF slanje pri
     * aktivaciji je "best effort" - vaučer ostaje ACTIVE i kad slanje pukne,
     * jer je sam kod validan bez obzira na PDF. Nema polja koje bi to obeležilo
     * niti dugmeta za ponovno slanje u panelu, pa propalo slanje ostaje
     * nevidljivo osim u logu. Ovaj test to dokumentuje kao poznato stanje -
     * ne kao potvrdu da je u redu. Vredi razmotriti voucherPdfSentAt +
     * "pošalji ponovo" po istom obrascu kao ConfirmationDocumentEmailService.
     */
    @Test
    void aktivacijaOstajeActiveIKadaPdfSlanjePukne() {
        GiftVoucher v = new GiftVoucher();
        v.setId(6L);
        v.setStatus(VoucherStatus.PENDING);
        v.setAmount(BigDecimal.valueOf(100));
        v.setCode("ESC-DDDD-EEEE-FFFF");
        v.setBuyerEmail("kupac2@example.com");
        when(voucherRepository.findById(6L)).thenReturn(Optional.of(v));
        when(voucherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(voucherPdfService.generate(any())).thenThrow(new RuntimeException("PDF servis nedostupan"));

        assertDoesNotThrow(() -> svc.activateVoucher(6L));
        assertEquals(VoucherStatus.ACTIVE, v.getStatus());
    }
}
