package com.escapii.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Zahtev kupca za gift putovanje.
 * Šalje se na POST /api/gifts/trips.
 * Identičan CustomDateInquiryRequest + polja za primaoca poklona.
 */
public record GiftTripInquiryRequest(

        @NotBlank(message = "Aerodrom je obavezan.")
        @Size(max = 10, message = "Aerodrom ne sme biti duži od 10 karaktera.")
        String airport,

        @Min(value = 1, message = "Minimalni broj putnika je 1.")
        @Max(value = 6, message = "Maksimalni broj putnika je 6.")
        int travelers,

        @NotNull(message = "Datum polaska je obavezan.")
        @Future(message = "Datum polaska mora biti u budućnosti.")
        LocalDate desiredDepartureDate,

        @Min(value = 1, message = "Minimalni broj noćenja je 1.")
        @Max(value = 3, message = "Maksimalni broj noćenja je 3.")
        int nights,

        @NotBlank(message = "Email kupca je obavezan.")
        @Email(message = "Email adresa nije validna.")
        @Size(max = 200, message = "Email ne sme biti duži od 200 karaktera.")
        String buyerEmail,

        @Size(max = 1000, message = "Napomena ne sme biti duža od 1000 karaktera.")
        String notes,

        @NotBlank(message = "Ime primaoca je obavezno.")
        @Size(max = 200, message = "Ime primaoca ne sme biti duže od 200 karaktera.")
        String recipientName,

        @NotBlank(message = "Email primaoca je obavezan.")
        @Email(message = "Email primaoca nije validan.")
        @Size(max = 200, message = "Email primaoca ne sme biti duži od 200 karaktera.")
        String recipientEmail,

        @Size(max = 500, message = "Poruka ne sme biti duža od 500 karaktera.")
        String giftMessage
) {}
