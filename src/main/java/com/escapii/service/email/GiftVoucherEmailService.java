package com.escapii.service.email;

import com.escapii.model.GiftVoucher;
import org.springframework.scheduling.annotation.Async;

public interface GiftVoucherEmailService {

    /** Šalje timu notifikaciju o novom vaučer upitu (async). */
    @Async
    void sendTeamAlert(GiftVoucher voucher);

    /**
     * Šalje kupcu email sa PDF vaučerom u prilogu, nakon što admin aktivira vaučer (async).
     * Kupac sam prosleđuje vaučer primaocu poklona.
     *
     * @param voucher aktivirani vaučer
     * @param pdfBytes generisani PDF vaučer (boarding-pass)
     */
    @Async
    void sendVoucherPdfToBuyer(GiftVoucher voucher, byte[] pdfBytes);
}
