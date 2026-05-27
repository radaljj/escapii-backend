package com.escapii.service.email;

import com.escapii.model.GiftVoucher;
import org.springframework.scheduling.annotation.Async;

public interface GiftVoucherEmailService {

    /** Šalje timu notifikaciju o novom vaučer upitu (async). */
    @Async
    void sendTeamAlert(GiftVoucher voucher);

    /** Šalje primaocu email sa vaučer kodom nakon aktivacije (async). */
    @Async
    void sendVoucherToRecipient(GiftVoucher voucher);
}
