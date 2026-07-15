package com.escapii.service.invoice;

import java.time.LocalDate;

public record InvoiceData(
        String invoiceNumber,
        LocalDate issuedAt,
        LocalDate dueDate,

        // Kupac
        String clientFirstName,
        String clientLastName,
        String clientEmail,
        String clientPhone,

        // Rezervacija
        String bookingRef,
        LocalDate departureDate,
        LocalDate returnDate,
        int numberOfTravelers,

        // Iznosi
        int subtotalEur,
        int voucherDiscountEur,
        String voucherCode,
        int totalEur,

        // Podaci firme
        String companyName,
        String companyAddress,
        String companyPib,
        String companyMb,
        String companyAccount,
        String companyBank,
        String companyEmail,
        String companyWebsite,

        // IPS QR kod (PNG data URI)
        String ipsQrDataUri
) {
    public boolean hasVoucher() {
        return voucherDiscountEur > 0 && voucherCode != null;
    }

    public String clientFullName() {
        return clientFirstName + " " + clientLastName;
    }
}
