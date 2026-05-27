package com.escapii.dto;

import com.escapii.model.GiftVoucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Javni reveal response — vraća se primaocu koji unese kod na /poklon stranici.
 * Sadrži samo informacije koje primalac sme da vidi (bez buyer emaila).
 */
public record GiftVoucherRevealResponse(
        boolean valid,
        BigDecimal amount,
        String recipientName,
        String buyerName,
        String giftMessage,
        LocalDateTime expiresAt,
        String message
) {
    public static GiftVoucherRevealResponse ok(GiftVoucher v) {
        return new GiftVoucherRevealResponse(
                true,
                v.getAmount(),
                v.getRecipientName(),
                v.getBuyerName(),
                v.getGiftMessage(),
                v.getExpiresAt(),
                null
        );
    }

    public static GiftVoucherRevealResponse invalid() {
        return new GiftVoucherRevealResponse(false, null, null, null, null, null,
                "Vaučer kod nije validan ili nije aktivan.");
    }
}
