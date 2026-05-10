package com.escapii.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request za kreiranje privatnog termina iz admin panela.
 * POST /api/admin/dates/{id}/make-private
 */
public record MakePrivateRequest(

    /** Broj mesta koji se rezerviše za korisnika koji je poslao upit. */
    @NotNull(message = "Broj putnika je obavezan.")
    @Min(value = 1, message = "Minimalni broj putnika je 1.")
    @Max(value = 50, message = "Maksimalni broj putnika je 50.")
    Integer travelers,

    /**
     * Koliko sati je link validan.
     * Podrazumevano: 72 sata (3 dana).
     */
    Integer expiresInHours
) {
    /** Vraća expiresInHours ili podrazumevanu vrednost 72. */
    public int effectiveExpiry() {
        return (expiresInHours != null && expiresInHours > 0) ? expiresInHours : 72;
    }
}
