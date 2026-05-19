package com.escapii.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request za kreiranje privatnog termina direktno iz upita.
 * POST /api/admin/inquiries/{id}/create-private-date
 *
 * Backend čita datume iz CustomDateInquiry — nema race conditiona,
 * termin se kreira kao privatan od prvog trenutka.
 */
public record CreatePrivateDateRequest(

    @NotNull(message = "Cena po osobi je obavezna.")
    @Min(value = 1, message = "Cena po osobi mora biti pozitivna.")
    Integer pricePerPerson,

    @NotNull(message = "Broj putnika je obavezan.")
    @Min(value = 1, message = "Minimalni broj putnika je 1.")
    @Max(value = 50, message = "Maksimalni broj putnika je 50.")
    Integer travelers,

    /** Koliko sati je link validan. Podrazumevano: 48. */
    Integer expiresInHours
) {
    public int effectiveExpiry() {
        return (expiresInHours != null && expiresInHours > 0) ? expiresInHours : 48;
    }
}
