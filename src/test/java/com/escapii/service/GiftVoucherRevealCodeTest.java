package com.escapii.service;

import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.service.email.GiftVoucherEmailService;
import com.escapii.service.impl.GiftVoucherServiceImpl;
import com.escapii.service.voucher.VoucherPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Novčani vaučer na /poklon: kod kopiran sa PDF-a (razmak između svakog slova) ili ukucan
 * malim slovima bez crtica mora da nađe isti vaučer kao kod iz QR linka.
 */
@ExtendWith(MockitoExtension.class)
class GiftVoucherRevealCodeTest {

    @Mock private GiftVoucherRepository voucherRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GiftVoucherEmailService emailService;
    @Mock private VoucherPdfService voucherPdfService;

    private static GiftVoucher aktivan() {
        GiftVoucher v = new GiftVoucher();
        v.setCode("ESC-MVV9-KZ27-SP2H");
        v.setAmount(new BigDecimal("100"));
        v.setStatus(VoucherStatus.ACTIVE);
        v.setBuyerName("Ana Anić");
        return v;
    }

    @Test
    void revealKodaKopiranogIzPdfa_iUkucanogBezCrtica() {
        when(voucherRepository.findByCode("ESC-MVV9-KZ27-SP2H")).thenReturn(Optional.of(aktivan()));
        GiftVoucherServiceImpl svc = new GiftVoucherServiceImpl(voucherRepository, bookingRepository, emailService, voucherPdfService);

        assertTrue(svc.reveal("E S C - M V V 9 - K Z 2 7 - S P 2 H").valid(), "kopirano iz PDF-a, sa razmacima");
        assertTrue(svc.reveal("escmvv9kz27sp2h").valid(), "ukucano malim slovima bez crtica");
        assertEquals(new BigDecimal("100"), svc.reveal("ESC-MVV9-KZ27-SP2H").amount());

        verify(voucherRepository, times(3)).findByCode("ESC-MVV9-KZ27-SP2H");
        verify(voucherRepository, never()).findByCode(contains(" "));
    }
}
