package com.escapii.service.email;

import com.escapii.model.Booking;
import com.escapii.model.GiftVoucher;

public interface InvoiceEmailService {
    void sendInvoiceToClient(Booking booking, byte[] pdfBytes, String invoiceNumber);
    void sendVoucherInvoiceToClient(GiftVoucher voucher, byte[] pdfBytes, String invoiceNumber);
}
