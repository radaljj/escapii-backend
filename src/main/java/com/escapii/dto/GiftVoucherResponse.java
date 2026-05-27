package com.escapii.dto;

import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Odgovor za gift vaučer.
 * Kod se vraća SAMO admin endpointima — javni endpointima nikad ne vraća kod.
 */
public record GiftVoucherResponse(
        Long id,
        BigDecimal amount,
        VoucherStatus status,
        String buyerEmail,
        String buyerName,
        String recipientEmail,
        String recipientName,
        String giftMessage,
        LocalDateTime createdAt,
        LocalDateTime activatedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        Long usedInBookingRef,
        // Kod se uključuje samo za admin pregled
        String code
) {
    public GiftVoucherResponse(GiftVoucher v) {
        this(
                v.getId(),
                v.getAmount(),
                v.getStatus(),
                v.getBuyerEmail(),
                v.getBuyerName(),
                v.getRecipientEmail(),
                v.getRecipientName(),
                v.getGiftMessage(),
                v.getCreatedAt(),
                v.getActivatedAt(),
                v.getExpiresAt(),
                v.getUsedAt(),
                v.getUsedInBookingRef(),
                v.getCode()
        );
    }
}
