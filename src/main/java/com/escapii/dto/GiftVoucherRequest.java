package com.escapii.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Zahtev za kupovinu gift vaučera.
 * Kupac bira iznos, unosi email i opciono ime koje će pisati na vaučeru,
 * kao i ličnu poruku. PDF vaučer se šalje isključivo na buyerEmail -
 * kupac ga sam prosljeđuje kome želi.
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

        @Size(max = 200, message = "Ime ne sme biti duže od 200 karaktera")
        String buyerName,

        @Size(max = 500, message = "Poruka ne sme biti duža od 500 karaktera")
        String giftMessage
) {}
