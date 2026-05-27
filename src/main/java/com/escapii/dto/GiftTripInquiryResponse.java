package com.escapii.dto;

import com.escapii.model.GiftTripInquiry;
import com.escapii.model.InquiryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GiftTripInquiryResponse(
        Long id,
        String airport,
        Integer travelers,
        LocalDate desiredDepartureDate,
        Integer nights,
        String buyerEmail,
        String notes,
        String recipientName,
        String recipientEmail,
        String giftMessage,
        InquiryStatus status,
        BigDecimal price,
        String privateToken,
        LocalDateTime createdAt,
        LocalDateTime closedAt
) {
    public GiftTripInquiryResponse(GiftTripInquiry i) {
        this(
                i.getId(),
                i.getAirport(),
                i.getTravelers(),
                i.getDesiredDepartureDate(),
                i.getNights(),
                i.getBuyerEmail(),
                i.getNotes(),
                i.getRecipientName(),
                i.getRecipientEmail(),
                i.getGiftMessage(),
                i.getStatus(),
                i.getPrice(),
                i.getPrivateToken(),
                i.getCreatedAt(),
                i.getClosedAt()
        );
    }
}
