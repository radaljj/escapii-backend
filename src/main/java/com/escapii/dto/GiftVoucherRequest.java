package com.escapii.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Zahtev za kupovinu gift vaučera.
 * Kupac bira iznos, unosi kontakt podatke i informacije o primaocu.
 */
public record GiftVoucherRequest(

        @NotNull(message = "Iznos vaučera je obavezan")
        @DecimalMin(value = "50.00", message = "Minimalni iznos vaučera je 50 EUR")
        @DecimalMax(value = "5000.00", message = "Maksimalni iznos vaučera je 5000 EUR")
        BigDecimal amount,

        @NotBlank(message = "Email kupca je obavezan")
        @Email(message = "Email kupca nije validan")
        @Size(max = 200)
        String buyerEmail,

        @Size(max = 200, message = "Ime kupca ne sme biti duže od 200 karaktera")
        String buyerName,

        @NotBlank(message = "Email primaoca je obavezan")
        @Email(message = "Email primaoca nije validan")
        @Size(max = 200)
        String recipientEmail,

        @NotBlank(message = "Ime primaoca je obavezno")
        @Size(max = 200, message = "Ime primaoca ne sme biti duže od 200 karaktera")
        String recipientName,

        @Size(max = 500, message = "Poruka ne sme biti duža od 500 karaktera")
        String giftMessage
) {}
