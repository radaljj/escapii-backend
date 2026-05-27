package com.escapii.service.email;

import com.escapii.model.GiftTripInquiry;
import org.springframework.scheduling.annotation.Async;

public interface GiftTripEmailService {

    /** Šalje timu notifikaciju o novom gift trip upitu (async). */
    @Async
    void sendTeamAlert(GiftTripInquiry inquiry);
}
