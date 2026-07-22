package com.escapii.service.email;

import com.escapii.model.Booking;
import com.escapii.model.GiftVoucher;

public interface InvoiceEmailService {
    /** @return true ako je mejl stvarno otišao (vidi ConfirmationDocumentEmailService). */
    boolean sendInvoiceToClient(Booking booking, byte[] pdfBytes, String invoiceNumber);
    boolean sendVoucherInvoiceToClient(GiftVoucher voucher, byte[] pdfBytes, String invoiceNumber);
}
