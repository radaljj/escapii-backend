package com.escapii.dto;

import com.escapii.model.GiftVoucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Javni reveal response — vraća se korisniku koji unese kod na /poklon stranici.
 */
public record GiftVoucherRevealResponse(
        boolean valid,
        BigDecimal amount,
        String buyerName,
        String giftMessage,
        LocalDateTime activatedAt,
        LocalDateTime expiresAt,
        String message
) {
    public static GiftVoucherRevealResponse ok(GiftVoucher v) {
        return new GiftVoucherRevealResponse(
                true,
                v.getAmount(),
                v.getBuyerName(),
                v.getGiftMessage(),
                v.getActivatedAt(),
                v.getExpiresAt(),
                null
        );
    }

    public static GiftVoucherRevealResponse invalid() {
        return new GiftVoucherRevealResponse(false, null, null, null, null, null,
                "Vaučer kod nije validan ili nije aktivan.");
    }
}
