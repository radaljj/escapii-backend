package com.escapii.dto;

import com.escapii.model.AgencyInvoice;
import com.escapii.model.AgencyInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Zbirna faktura agenciji kako je vidi panel (bez PDF bajtova). */
public record AgencyInvoiceResponse(
        Long id,
        String invoiceNumber,
        Long agencyId,
        String agencyName,
        String agencyEmail,
        String description,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal amount,
        int bookingCount,
        AgencyInvoiceStatus status,
        LocalDate issuedAt,
        LocalDate dueDate,
        LocalDateTime sentAt,
        LocalDateTime paidAt,
        LocalDateTime voidedAt,
        String voidReason,
        List<String> bookingRefs
) {
    public static AgencyInvoiceResponse from(AgencyInvoice i, List<String> bookingRefs) {
        return new AgencyInvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getAgencyId(), i.getAgencyName(),
                i.getAgencyEmail(), i.getDescription(), i.getPeriodFrom(), i.getPeriodTo(), i.getAmount(),
                i.getBookingCount() == null ? 0 : i.getBookingCount(), i.getStatus(), i.getIssuedAt(), i.getDueDate(),
                i.getSentAt(), i.getPaidAt(), i.getVoidedAt(), i.getVoidReason(), bookingRefs);
    }
}
