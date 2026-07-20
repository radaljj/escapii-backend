package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.GiftVoucherResponse;

/**
 * Generisanje i slanje profaktura (rezervacije i poklon vaučeri) - IPS QR kod,
 * PDF, i mejl klijentu. Admin-only, dozvoljeno samo za PENDING rezervacije/vaučere.
 */
public interface InvoiceService {

    AdminBookingResponse sendInvoice(Long bookingId);

    GiftVoucherResponse sendVoucherInvoice(Long voucherId);
}
