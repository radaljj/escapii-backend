package com.escapii.service.email;

import com.escapii.model.GiftVoucher;
import com.escapii.service.AppErrorService;
import com.escapii.service.email.core.EmailSender;
import com.escapii.service.email.impl.GiftVoucherEmailServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Neuspeh slanja PDF vaučera kupcu je ranije bio nevidljiv adminu - metoda je
 * @Async pa pozivalac (GiftVoucherServiceImpl) ne može uhvatiti grešku koja se
 * desi u samom slanju, jedini trag je bio log.warn koji niko ne čita. Sad mora
 * da se prijavi u AppErrorService da se pojavi u admin "🚨 Greške" tabu.
 */
class GiftVoucherPdfErrorVisibilityTest {

    private static void set(Object target, String field, Object val) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }

    private GiftVoucher voucher(long id) {
        GiftVoucher v = new GiftVoucher();
        v.setId(id);
        v.setCode("ESC-TEST-CODE-000" + id);
        v.setAmount(BigDecimal.valueOf(100));
        v.setBuyerEmail("kupac@example.com");
        return v;
    }

    private GiftVoucherEmailServiceImpl svc(EmailSender sender, AppErrorService appErrorService) throws Exception {
        var s = new GiftVoucherEmailServiceImpl(sender);
        set(s, "appErrorService", appErrorService);
        set(s, "frontendUrl", "https://escapii.rs");
        set(s, "contactEmail", "info@escapii.rs");
        return s;
    }

    @Test
    void neuspesnoSlanjePdfSeSnimaUAppError() throws Exception {
        EmailSender failingSender = mock(EmailSender.class);
        when(failingSender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(false);
        AppErrorService appErrorService = mock(AppErrorService.class);

        svc(failingSender, appErrorService).sendVoucherPdfToBuyer(voucher(9L), new byte[]{1, 2, 3});

        verify(appErrorService).record(contains("gift-voucher-pdf"), eq(0), any(Exception.class));
    }

    @Test
    void uspesnoSlanjeNeSnimaGresku() throws Exception {
        EmailSender okSender = mock(EmailSender.class);
        when(okSender.sendWithAttachment(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(true);
        AppErrorService appErrorService = mock(AppErrorService.class);

        svc(okSender, appErrorService).sendVoucherPdfToBuyer(voucher(10L), new byte[]{1, 2, 3});

        verifyNoInteractions(appErrorService);
    }
}
