package com.escapii.service.email;

import com.escapii.model.Booking;

public interface InvoiceEmailService {
    void sendInvoiceToClient(Booking booking, byte[] pdfBytes, String invoiceNumber);
}
