package com.escapii.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Zahtev korisnika za custom termin.
 * Šalje se na POST /api/inquiries/custom-date.
 */
public record CustomDateInquiryRequest(

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

    @NotBlank(message = "Email adresa je obavezna.")
    @Email(message = "Email adresa nije validna.")
    @Size(max = 200, message = "Email ne sme biti duži od 200 karaktera.")
    String email,

    @Size(max = 1000, message = "Napomena ne sme biti duža od 1000 karaktera.")
    String notes
) {}
