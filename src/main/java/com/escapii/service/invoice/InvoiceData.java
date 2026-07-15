package com.escapii.service.invoice;

import java.time.LocalDate;

public record InvoiceData(
        String invoiceNumber,
        LocalDate issuedAt,
        LocalDate dueDate,

        // Naziv stavke (rezervacija ili poklon vaučer)
        String itemName,

        // Kupac
        String clientFirstName,
        String clientLastName,
        String clientEmail,
        String clientPhone,

        // Stavka — nullable za vaučer fakturu
        String bookingRef,
        LocalDate departureDate,
        LocalDate returnDate,
        Integer numberOfTravelers,

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

    public boolean hasTravelDates() {
        return departureDate != null && returnDate != null;
    }

    public String clientFullName() {
        return clientFirstName + (clientLastName != null && !clientLastName.isBlank() ? " " + clientLastName : "");
    }
}
